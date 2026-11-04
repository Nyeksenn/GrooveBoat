package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.entities.query.QArtistModel;
import org.gnome.gdk.MemoryFormat;
import org.gnome.gdk.MemoryTexture;
import org.gnome.gdk.Texture;
import org.gnome.gdkpixbuf.Pixbuf;
import org.javagi.base.GErrorException;
import org.javagi.base.Out;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-wide cache of decoded album covers and artist images, at the size levels of {@link CoverSizes}. Each image
 * stays on disk as one file (cached by {@code LibrarySync}); a level is decoded from it on first use, so a 40 px queue
 * row holds a 64 px texture instead of the full-size image. The image paths come from the database.
 *
 * <p>Textures are bounded by a byte-weighted LRU. Evicting one is harmless: widgets keep their own reference.
 * Decoding runs on a loader-owned virtual-thread pool, never the ForkJoin common pool, and only creates textures
 * (immutable, thread-safe), no custom GObjects.
 */
public class CoverLoader {
    private static final Logger logger = LoggerFactory.getLogger(CoverLoader.class);
    /** Upper bound for decoded pixel data (RGBA) kept in the cache. */
    static final long MAX_CACHE_BYTES = 64L * 1024 * 1024;

    private record Key(CoverKind kind, String id, int bucket) {
    }

    private record Subject(CoverKind kind, String id) {
    }

    private final WeightedLru<Key, Texture> textures = new WeightedLru<>(MAX_CACHE_BYTES, CoverLoader::weight);
    /** Image file per album/artist; empty when it has none, so a missing cover isn't looked up again and again. */
    private final ConcurrentHashMap<Subject, Optional<Path>> paths = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key, CompletableFuture<Optional<Texture>>> inFlight = new ConcurrentHashMap<>();
    private final ExecutorService executor =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("grooveboat-cover-", 0).factory());
    /** Bumped by {@link #clear()}, so a decode that started before a logout doesn't repopulate the cache. */
    private final AtomicInteger epoch = new AtomicInteger();

    private CoverLoader() {
    }

    // Holder class - loaded on first access to getInstance()
    private static class Holder {
        private static final CoverLoader INSTANCE = new CoverLoader();
    }

    public static CoverLoader getInstance() {
        return Holder.INSTANCE;
    }

    private static long weight(Texture texture) {
        return 4L * texture.getWidth() * texture.getHeight();
    }

    /** The cached texture of exactly this level, or {@code null}. Cheap enough for the main thread. */
    public @Nullable Texture peekExact(CoverKind kind, String id, int bucket) {
        return textures.get(new Key(kind, id, bucket));
    }

    /**
     * The best cached texture for this level: the exact one, else the next larger level (downsampled by the GPU),
     * else the closest smaller one. {@code null} if the image isn't cached at any level.
     */
    public @Nullable Texture peekBest(CoverKind kind, String id, int bucket) {
        var exact = peekExact(kind, id, bucket);
        if (exact != null) {
            return exact;
        }
        for (var candidate : CoverSizes.BUCKETS) {
            if (candidate > bucket) {
                var larger = textures.get(new Key(kind, id, candidate));
                if (larger != null) {
                    return larger;
                }
            }
        }
        for (var i = CoverSizes.BUCKETS.length - 1; i >= 0; i--) {
            if (CoverSizes.BUCKETS[i] < bucket) {
                var smaller = textures.get(new Key(kind, id, CoverSizes.BUCKETS[i]));
                if (smaller != null) {
                    return smaller;
                }
            }
        }
        return null;
    }

    /** Decodes (or finds) the texture of this level in the background; empty if the image is missing or broken. */
    public CompletableFuture<Optional<Texture>> loadAsync(CoverKind kind, String id, int bucket) {
        var key = new Key(kind, id, bucket);
        var cached = textures.get(key);
        if (cached != null) {
            return CompletableFuture.completedFuture(Optional.of(cached));
        }
        return inFlight.computeIfAbsent(key, k -> {
            var started = epoch.get();
            var future = CompletableFuture.supplyAsync(() -> decode(k, started), executor);
            future.whenComplete((_, _) -> inFlight.remove(k));
            return future;
        });
    }

    /** Blocking variant of {@link #loadAsync}, for callers that already run off the main thread. */
    public Optional<Texture> load(CoverKind kind, String id, int bucket) {
        return loadAsync(kind, id, bucket).join();
    }

    private Optional<Texture> decode(Key key, int startedEpoch) {
        var path = paths.computeIfAbsent(new Subject(key.kind(), key.id()), s -> Optional.ofNullable(lookupPath(s)));
        if (path.isEmpty()) {
            return Optional.empty();
        }
        var texture = decode(path.get(), key.bucket(), key.kind() + " " + key.id());
        if (texture == null) {
            return Optional.empty();
        }
        if (epoch.get() == startedEpoch) {
            textures.put(key, texture);
        }
        return Optional.of(texture);
    }

    private static @Nullable Path lookupPath(Subject subject) {
        if (subject.kind() == CoverKind.ALBUM) {
            var alb = QAlbumModel.alias();
            var album = new QAlbumModel().select(alb.id, alb.coverPath).id.equalTo(subject.id()).findOne();
            return album == null ? null : album.getCoverPath();
        }
        var art = QArtistModel.alias();
        var artist = new QArtistModel().select(art.id, art.artistImagePath).id.equalTo(subject.id()).findOne();
        return artist == null ? null : artist.getArtistImagePath();
    }

    /** Decodes the file at {@code bucket} pixels on its longer edge; never scales an image up. */
    private static @Nullable Texture decode(Path path, int bucket, String what) {
        try {
            var file = path.toString();
            var width = new Out<Integer>();
            var height = new Out<Integer>();
            var format = Pixbuf.getFileInfo(file, width, height);
            if (format == null || Math.max(width.get(), height.get()) <= bucket) {
                return Texture.fromFilename(file);
            }
            // gdk-pixbuf decodes JPEGs directly at a reduced size, so small levels are cheap.
            var pixbuf = Pixbuf.fromFileAtScale(file, bucket, bucket, true);
            return pixbuf == null ? null : toTexture(pixbuf);
        } catch (GErrorException e) {
            logger.warn("Couldn't load image for {}: {}", what, e.getMessage());
            return null;
        }
    }

    /** Uploads the pixels as they are; opaque covers (JPEG) skip the alpha channel, a quarter less texture memory. */
    private static Texture toTexture(Pixbuf pixbuf) {
        var format = pixbuf.getHasAlpha() ? MemoryFormat.R8G8B8A8 : MemoryFormat.R8G8B8;
        return new MemoryTexture(pixbuf.getWidth(), pixbuf.getHeight(), format, pixbuf.readPixelBytes(),
                pixbuf.getRowstride());
    }

    /** Drops all cached textures and paths, e.g. on logout or after a sync replaced the images. */
    public void clear() {
        epoch.incrementAndGet();
        textures.clear();
        paths.clear();
    }

    /** Drops the cached artist images, so they are read again after an artist refresh. */
    public void clearArtists() {
        epoch.incrementAndGet();
        textures.removeIf(key -> key.kind() == CoverKind.ARTIST);
        paths.keySet().removeIf(subject -> subject.kind() == CoverKind.ARTIST);
    }
}
