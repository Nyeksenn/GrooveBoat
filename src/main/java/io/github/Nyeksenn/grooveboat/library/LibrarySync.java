package io.github.Nyeksenn.grooveboat.library;

import io.ebean.DB;
import io.ebean.Database;
import io.ebean.DuplicateKeyException;
import io.github.Nyeksenn.grooveboat.backend.MusicBackend;
import io.github.Nyeksenn.grooveboat.backend.RemoteAlbum;
import io.github.Nyeksenn.grooveboat.backend.RemoteArtistRef;
import io.github.Nyeksenn.grooveboat.backend.RemoteSong;
import io.github.Nyeksenn.grooveboat.entities.AlbumModel;
import io.github.Nyeksenn.grooveboat.entities.ArtistModel;
import io.github.Nyeksenn.grooveboat.entities.SongModel;
import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.entities.query.QArtistModel;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import io.github.Nyeksenn.grooveboat.utils.AppSettings;
import io.github.Nyeksenn.grooveboat.utils.Helpers;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps the local cache in sync with the server of the current session. Works with any {@link MusicBackend}; it is
 * the only place that writes server data into the database.
 */
public final class LibrarySync implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(LibrarySync.class);
    private static final int IMAGE_SIZE = 800;
    private static final String ARTIST_UPSERT = """
            MERGE INTO ARTIST (id, name, artist_image_path, image_checked)
            KEY (id)
            VALUES (:id, :name, :artistImagePath, :imageChecked)
            """;

    private final MusicBackend backend;
    private final Database db = DB.getDefault();
    private final ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
    private final ReentrantLock syncLock = new ReentrantLock();
    /** Held while song rows are built and written, so a logout can wait for them before wiping the tables. */
    private final ReentrantLock saveLock = new ReentrantLock();
    private volatile boolean cancelled = false;
    /** Albums whose song list was compared with the server in this session. */
    private final Set<String> revalidated = ConcurrentHashMap.newKeySet();

    public LibrarySync(MusicBackend backend) {
        this.backend = backend;
    }

    public MusicBackend backend() {
        return backend;
    }

    private void checkCancelled() {
        if (cancelled) {
            throw new CancellationRequested();
        }
    }

    /**
     * Runs the tasks concurrently, each on its own virtual thread, and returns their results in order once all have
     * finished. A failing task fails the call with the task's own exception (a {@link CancellationRequested} when the
     * sync was cancelled), so callers handle it like an error in their own code.
     */
    private <T> List<T> runAll(List<Callable<T>> tasks) {
        List<Future<T>> futures;
        try {
            futures = exec.invokeAll(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationRequested();
        }
        var results = new ArrayList<T>(tasks.size());
        for (var future : futures) {
            try {
                results.add(future.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CancellationRequested();
            } catch (ExecutionException e) {
                var cause = e.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException("Sync task failed", cause);
            }
        }
        checkCancelled();
        return results;
    }

    /** Thrown inside sync tasks to unwind after {@link #cancelAndAwait}. */
    private static final class CancellationRequested extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CancellationRequested() {
            super("Library sync cancelled", null, false, false);
        }
    }

    /**
     * Fetches albums added since the last sync (or everything, when {@code fullRefresh}), caches their covers and
     * refreshes artist details.
     *
     * @return the number of albums that were new to the cache; 0 when there was nothing new or the sync failed or was
     * cancelled (the failure is logged)
     */
    public int refresh(boolean fullRefresh) {
        syncLock.lock();
        try {
            checkCancelled();
            var settings = AppSettings.get();
            var lastRefresh = fullRefresh
                    ? Instant.EPOCH
                    : Instant.ofEpochSecond(settings.getUint64(AppSettings.LAST_SYNC));

            // Fetch before wiping anything: a failed full refresh (e.g. server unreachable) keeps the cache.
            var fetched = backend.albumsNewerThan(lastRefresh);
            checkCancelled();
            if (fullRefresh) {
                settings.setUint64(AppSettings.LAST_SYNC, 0L);
                try {
                    Helpers.truncateDB(db);
                } catch (SQLException e) {
                    logger.warn("Couldn't truncate tables", e);
                }
            }
            // The cutoff has second precision, so albums from the last synced second can come back.
            var known = new HashSet<String>();
            if (!fullRefresh && !fetched.isEmpty()) {
                known.addAll(db.find(AlbumModel.class).findIds().stream().map(Object::toString).toList());
            }
            var newAlbums = fetched.stream().filter(a -> !known.contains(a.id())).toList();
            logger.info("Found {} new albums", newAlbums.size());
            if (newAlbums.isEmpty()) {
                // The artist page shows album artists' images; fetch those still missing (e.g. after the repair
                // migration cleared broken paths) without querying every artist again.
                updateArtists(uncheckedArtists());
                return 0;
            }

            var albumTasks = new ArrayList<Callable<AlbumModel>>(newAlbums.size());
            for (var album : newAlbums) {
                albumTasks.add(() -> {
                    checkCancelled();
                    var albumArtists = resolveArtists(album.artists());
                    Path coverPath = null;
                    if (album.imageId() != null) {
                        try {
                            coverPath = backend.cacheImage(album.imageId(), IMAGE_SIZE);
                        } catch (IOException e) {
                            logger.warn("Unable to cache cover art for album {}: {}", album.title(), e.getMessage());
                        }
                    }
                    return AlbumModel.from(album, coverPath, albumArtists);
                });
            }

            var albumModels = runAll(albumTasks);
            db.saveAll(albumModels);
            // The newest album's server timestamp, not the local clock: the two can differ.
            var newest = newAlbums.stream().map(RemoteAlbum::created).max(Instant::compareTo).orElse(Instant.EPOCH);
            if (newest.getEpochSecond() > lastRefresh.getEpochSecond()) {
                settings.setUint64(AppSettings.LAST_SYNC, newest.getEpochSecond());
            }

            // Only the artists these albums brought in (and any never checked): the rest of the library is unchanged.
            var newArtistIds = newAlbums.stream().flatMap(a -> a.artists().stream()).map(RemoteArtistRef::id)
                    .collect(Collectors.toSet());
            var toUpdate = new LinkedHashMap<String, ArtistModel>();
            new QArtistModel().id.isIn(newArtistIds).findList().forEach(a -> toUpdate.put(a.getId(), a));
            uncheckedArtists().forEach(a -> toUpdate.putIfAbsent(a.getId(), a));
            updateArtists(new ArrayList<>(toUpdate.values()));
            return newAlbums.size();
        } catch (CancellationRequested e) {
            logger.info("Library sync cancelled");
        } catch (IOException e) {
            logger.warn("Couldn't fetch albums: {}", e.getMessage());
        } catch (RuntimeException e) {
            logger.error("Library sync failed", e);
        } finally {
            syncLock.unlock();
        }
        return 0;
    }

    /**
     * Album artists without a cached image that were never checked: an artist the server has no image for is checked
     * once, not on every start.
     */
    private static List<ArtistModel> uncheckedArtists() {
        return new QArtistModel().albums.isNotEmpty().artistImagePath.isNull().imageChecked.isFalse().findList();
    }

    /** Asks the server for each artist's details and image and stores them. */
    private void updateArtists(List<ArtistModel> artists) {
        try (var tx = DB.beginTransaction()) {
            for (var artist : artists) {
                checkCancelled();
                try {
                    var remote = backend.artist(artist.getId());
                    Path imagePath = null;
                    if (remote.imageId() != null) {
                        try {
                            imagePath = backend.cacheImage(remote.imageId(), IMAGE_SIZE);
                        } catch (IOException e) {
                            logger.debug("No image for artist {}", remote.name());
                        }
                    }
                    // Checked: the server has no image, or the image is cached. A failed download stays unchecked.
                    var checked = remote.imageId() == null || imagePath != null;
                    DB.sqlUpdate(ARTIST_UPSERT)
                            .setParameter("id", remote.id())
                            .setParameter("imageChecked", checked)
                            .setParameter("name", ArtistModel.clampName(remote.name()))
                            // Ebean maps Path columns as file URIs and reads them back with Paths.get(URI); a
                            // plain path here made every later load of this artist fail.
                            .setParameter("artistImagePath", imagePath == null ? null : imagePath.toUri().toString())
                            .execute();
                } catch (IOException e) {
                    logger.warn("Couldn't retrieve artist {}", artist.getName());
                }
            }
            tx.commit();
        }
    }

    private static ArrayList<ArtistModel> resolveArtists(List<RemoteArtistRef> artists) {
        var artistList = new ArrayList<ArtistModel>(3);
        for (var artist : artists) {
            var savedArtist = new QArtistModel().id.equalTo(artist.id()).findOne();
            if (savedArtist == null) {
                try {
                    var newArtist = ArtistModel.from(artist);
                    newArtist.save();
                    artistList.add(newArtist);
                } catch (DuplicateKeyException e) {
                    // Another sync task created it concurrently.
                    artistList.add(new QArtistModel().id.equalTo(artist.id()).findOne());
                }
            } else {
                artistList.add(savedArtist);
            }
        }
        return artistList;
    }

    /**
     * Returns the songs of an album. When the cache has all of them (as many as the album's song count), they are
     * returned at once and the server is asked in the background, once per album and session, whether anything
     * changed; otherwise the songs are fetched from the server and upserted before returning.
     */
    public List<SongModel> getSongs(String albumId) {
        try {
            checkCancelled();
            var cached = cachedIfComplete(albumId);
            if (cached != null) {
                if (revalidated.add(albumId)) {
                    try {
                        exec.execute(() -> loadSongsFromServer(albumId));
                    } catch (RejectedExecutionException e) {
                        logger.debug("Not checking album {} for changes: the sync is closed", albumId);
                    }
                }
                return cached;
            }
        } catch (CancellationRequested e) {
            logger.info("Library sync cancelled");
            return new ArrayList<>();
        } catch (RuntimeException e) {
            logger.error("Loading songs failed", e);
            return new ArrayList<>();
        }
        return loadSongsFromServer(albumId);
    }

    /** The cached songs of an album if there are as many as the album's song count, otherwise {@code null}. */
    private @Nullable List<SongModel> cachedIfComplete(String albumId) {
        saveLock.lock();
        try {
            checkCancelled();
            var album = new QAlbumModel().id.equalTo(albumId).findOne();
            if (album == null || album.getSongCount() <= 0) {
                return null;
            }
            var cached = cachedSongs(albumId);
            return cached.size() == album.getSongCount() ? cached : null;
        } finally {
            saveLock.unlock();
        }
    }

    /** Fetches the songs of an album from the server and upserts them. Falls back to the cache when offline. */
    private List<SongModel> loadSongsFromServer(String albumId) {
        List<RemoteSong> songList;
        try {
            checkCancelled();
            songList = backend.songsOfAlbum(albumId);
        } catch (IOException e) {
            // Offline or server trouble: whatever the cache has is better than an empty page.
            logger.warn("Couldn't retrieve songs for album {}: {}", albumId, e.getMessage());
            return cachedSongs(albumId);
        } catch (CancellationRequested e) {
            logger.info("Library sync cancelled");
            return new ArrayList<>();
        } catch (RuntimeException e) {
            logger.error("Loading songs failed", e);
            return new ArrayList<>();
        }
        // Everything below reads and writes the cache. A logout waits for this lock before wiping the tables.
        saveLock.lock();
        try {
            checkCancelled();
            var songsSaved = cachedSongs(albumId);

            if (songsSaved.size() == songList.size()) {
                revalidated.add(albumId);
                return songsSaved;
            }

            var album = new QAlbumModel().id.equalTo(albumId).findOne();
            var songTasks = new ArrayList<Callable<SongModel>>(songList.size());
            for (var song : songList) {
                songTasks.add(() -> {
                    checkCancelled();
                    var savedSongModel = new QSongModel().id.equalTo(song.id()).findOne();
                    var artists = resolveArtists(song.artists());
                    var newSongModel = SongModel.from(song, artists, album);

                    if (savedSongModel == null) {
                        return newSongModel;
                    }
                    if (!savedSongModel.hasSameContent(newSongModel)) {
                        // Update the loaded row in place; saving a new bean with the same id would insert.
                        savedSongModel.setAllFields(newSongModel);
                    }
                    return savedSongModel;
                });
            }

            var songModelList = runAll(songTasks);
            db.saveAll(songModelList);
            if (album != null && album.getSongCount() != songList.size()) {
                album.setSongCount(songList.size());
                album.update();
            }
            revalidated.add(albumId);
            return songModelList;
        } catch (CancellationRequested e) {
            logger.info("Library sync cancelled");
            return new ArrayList<>();
        } catch (RuntimeException e) {
            logger.error("Loading songs failed", e);
            return new ArrayList<>();
        } finally {
            saveLock.unlock();
        }
    }

    private static List<SongModel> cachedSongs(String albumId) {
        return new QSongModel().album.id.equalTo(albumId).orderBy().discNumber.asc().track.asc().findList();
    }

    /**
     * Stops running and future sync work and waits up to {@code timeout} for an in-flight sync to unwind.
     *
     * @return whether the sync finished within the timeout
     */
    public boolean cancelAndAwait(Duration timeout) {
        cancelled = true;
        var deadline = System.nanoTime() + timeout.toNanos();
        try {
            // Both the album sync and in-flight song saves have to be done before the caller wipes the tables.
            if (!syncLock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return false;
            }
            syncLock.unlock();
            var remaining = Math.max(0L, deadline - System.nanoTime());
            if (!saveLock.tryLock(remaining, TimeUnit.NANOSECONDS)) {
                return false;
            }
            saveLock.unlock();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void close() {
        cancelled = true;
        exec.shutdownNow();
    }
}
