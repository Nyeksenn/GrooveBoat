package io.github.Nyeksenn.grooveboat.library;

import io.ebean.DB;
import io.ebean.Database;
import io.ebean.DuplicateKeyException;
import io.github.Nyeksenn.grooveboat.backend.MusicBackend;
import io.github.Nyeksenn.grooveboat.backend.RemoteArtistRef;
import io.github.Nyeksenn.grooveboat.entities.AlbumModel;
import io.github.Nyeksenn.grooveboat.entities.ArtistModel;
import io.github.Nyeksenn.grooveboat.entities.SongModel;
import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.entities.query.QArtistModel;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import io.github.Nyeksenn.grooveboat.utils.AppSettings;
import io.github.Nyeksenn.grooveboat.utils.Helpers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Keeps the local cache in sync with the server of the current session. Works with any {@link MusicBackend}; it is
 * the only place that writes server data into the database.
 */
public final class LibrarySync implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(LibrarySync.class);
    private static final int IMAGE_SIZE = 800;
    private static final String ARTIST_UPSERT = """
            MERGE INTO ARTIST (id, name, artist_image_path)
            KEY (id)
            VALUES (:id, :name, :artistImagePath)
            """;

    private final MusicBackend backend;
    private final Database db = DB.getDefault();
    private final ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
    private final ReentrantLock syncLock = new ReentrantLock();
    private volatile boolean cancelled = false;

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

    /** Thrown inside sync tasks to unwind after {@link #cancelAndAwait}. */
    private static final class CancellationRequested extends RuntimeException {
        CancellationRequested() {
            super("Library sync cancelled", null, false, false);
        }
    }

    /**
     * Fetches albums added since the last sync (or everything, when {@code fullRefresh}), caches their covers and
     * refreshes artist details.
     */
    public void refresh(boolean fullRefresh) {
        syncLock.lock();
        try {
            checkCancelled();
            var settings = AppSettings.get();
            if (fullRefresh) {
                settings.setUint64(AppSettings.LAST_SYNC, 0L);
                try {
                    Helpers.truncateDB(db);
                } catch (SQLException e) {
                    logger.warn("Couldn't truncate tables");
                }
            }
            var lastRefresh = Instant.ofEpochSecond(settings.getUint64(AppSettings.LAST_SYNC));
            var syncStarted = Instant.now();

            var newAlbums = backend.albumsNewerThan(lastRefresh);
            logger.info("Found {} new albums", newAlbums.size());
            if (newAlbums.isEmpty()) {
                settings.setUint64(AppSettings.LAST_SYNC, syncStarted.getEpochSecond());
                return;
            }

            var futures = new ArrayList<CompletableFuture<AlbumModel>>(newAlbums.size());
            for (var album : newAlbums) {
                futures.add(CompletableFuture.supplyAsync(() -> {
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
                    return new AlbumModel(album.id(), truncate(album.title(), 100), album.songCount(), album.year(),
                            album.duration(), coverPath, albumArtists);
                }, exec));
            }

            var albumModels = futures.stream().map(CompletableFuture::join).toList();
            checkCancelled();
            db.saveAll(albumModels);
            settings.setUint64(AppSettings.LAST_SYNC, syncStarted.getEpochSecond());

            updateArtists();
        } catch (CancellationRequested e) {
            logger.info("Library sync cancelled");
        } catch (CompletionException e) {
            if (e.getCause() instanceof CancellationRequested) {
                logger.info("Library sync cancelled");
            } else {
                logger.error("Album sync task failed: {}", e.getMessage());
            }
        } catch (IOException e) {
            logger.warn("Couldn't fetch albums: {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("Library sync failed: {}", e.getMessage());
        } finally {
            syncLock.unlock();
        }
    }

    private void updateArtists() {
        var artists = new QArtistModel().findList();
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
                    DB.sqlUpdate(ARTIST_UPSERT)
                            .setParameter("id", remote.id())
                            .setParameter("name", truncate(remote.name(), 200))
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

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static ArrayList<ArtistModel> resolveArtists(List<RemoteArtistRef> artists) {
        var artistList = new ArrayList<ArtistModel>(3);
        for (var artist : artists) {
            var savedArtist = new QArtistModel().id.equalTo(artist.id()).findOne();
            if (savedArtist == null) {
                try {
                    var newArtist = new ArtistModel(artist.id(), truncate(artist.name(), 200));
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

    /** Returns the songs of an album, fetching and upserting them from the server when the cache is incomplete. */
    public List<SongModel> getSongs(String albumId) {
        try {
            checkCancelled();
            var songList = backend.songsOfAlbum(albumId);
            var songsSaved = new QSongModel().album.id.equalTo(albumId)
                    .orderBy().discNumber.asc().track.asc().findList();

            if (songsSaved.size() == songList.size()) {
                return songsSaved;
            }

            var album = new QAlbumModel().id.equalTo(albumId).findOne();
            var futures = new ArrayList<CompletableFuture<SongModel>>(songList.size());
            for (var song : songList) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    checkCancelled();
                    var savedSongModel = new QSongModel().id.equalTo(song.id()).findOne();
                    var artists = resolveArtists(song.artists());
                    var newSongModel = new SongModel(
                            song.id(),
                            truncate(song.title(), 200),
                            song.track(),
                            song.year(),
                            song.duration(),
                            song.bitRate(),
                            truncate(song.contentType(), 50),
                            song.discNumber(),
                            artists,
                            album
                    );

                    if (savedSongModel == null) {
                        return newSongModel;
                    }
                    if (!newSongModel.equals(savedSongModel)) {
                        // Update the loaded row in place; saving a new bean with the same id would insert.
                        savedSongModel.setAllFields(newSongModel);
                    }
                    return savedSongModel;
                }, exec));
            }

            var songModelList = futures.stream().map(CompletableFuture::join).toList();
            checkCancelled();
            db.saveAll(songModelList);
            return songModelList;
        } catch (IOException e) {
            logger.warn("Couldn't retrieve songs for album {}: {}", albumId, e.getMessage());
            return new ArrayList<>();
        } catch (Exception e) {
            logger.warn("Loading songs failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Stops running and future sync work and waits up to {@code timeout} for an in-flight sync to unwind.
     *
     * @return whether the sync finished within the timeout
     */
    public boolean cancelAndAwait(Duration timeout) {
        cancelled = true;
        try {
            if (syncLock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                syncLock.unlock();
                return true;
            }
            return false;
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
