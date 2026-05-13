package io.github.Nyeksenn.grooveboat.client;

import io.ebean.DB;
import io.ebean.Database;
import io.ebean.DuplicateKeyException;
import io.github.Nyeksenn.grooveboat.deserializer.ShortArtist;
import io.github.Nyeksenn.grooveboat.deserializer.SubsonicResponse;
import io.github.Nyeksenn.grooveboat.entities.AlbumModel;
import io.github.Nyeksenn.grooveboat.entities.ArtistModel;
import io.github.Nyeksenn.grooveboat.entities.SongModel;
import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.entities.query.QArtistModel;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import io.github.Nyeksenn.grooveboat.utils.Helpers;
import org.gnome.gio.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
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

public class Subsonic implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(Subsonic.class);
    private final Settings settings = new Settings("io.github.Nyeksenn.grooveboat");

    private SubsonicClient client;
    private final Database db = DB.getDefault();
    private final ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
    private final ExecutorService threadExec = Executors.newThreadPerTaskExecutor(Executors.defaultThreadFactory());

    private void updateArtists () {
        final String sql = """
                MERGE INTO ARTIST (id, name, artist_image_path)
                KEY (id)
                VALUES (:id, :name, :artistImagePath)
                """;

        var artists = new QArtistModel().findList();
        try (var tx = DB.beginTransaction()) {
            for (var artist : artists) {
                try {
                    var remoteArtist = client.getArtist(artist.getId());
                    var artistImagePath = client.cacheCoverArt(artist.getId(), 800);
                    var remoteArtistModel = new ArtistModel(remoteArtist.id(), remoteArtist.name(), artistImagePath);
                    DB.sqlUpdate(sql)
                            .setParameter("id", remoteArtistModel.getId())
                            .setParameter("name", remoteArtistModel.getName())
                            .setParameter("artistImagePath", remoteArtistModel.getArtistImagePath())
                            .execute();
                } catch (IOException e) {
                    logger.warn("Couldn't retrieve artist {}", artist.getName());
                }
            }
            tx.commit();
        }
    }

    public void refreshRemoteData(boolean fullRefresh) {
        if (fullRefresh) {
            settings.setUint64("last-sync", 0L);
            try {
                Helpers.truncateDB(db);
            } catch (SQLException e) {
                logger.warn("Couldn't truncate tables");
            }
        }
        var lastRefresh = Instant.ofEpochSecond(settings.getUint64("last-sync"));

        try {
            List<CompletableFuture<AlbumModel>> futures = new ArrayList<>(200);

            var newAlbums = client.getNewAlbums(lastRefresh);

            // If no new albums we can return early
            if (newAlbums.isEmpty()) {
                return;
            }

            for (var album : newAlbums) {
                var future = CompletableFuture.supplyAsync(() -> {
                    var albumArtists = resolveArtists(album.artists());

                    Path coverPath;
                    try {
                        coverPath = client.cacheCoverArt(album.coverArt(), 800);
                    } catch (IOException e) {
                        coverPath = null;
                        logger.warn("Unable to cache cover art for album {}: {}", album.name(), e.getMessage());
                    }

                    return new AlbumModel(album.id(), album.name(), album.songCount(), album.year(), Duration.ofSeconds(album.duration()), coverPath, albumArtists);
                }, exec);
                futures.add(future);
            }

            var albumModelList = futures.stream().map(CompletableFuture::join).toList();
            db.saveAll(albumModelList);

            settings.setUint64("last-sync", Instant.now().getEpochSecond());

            // Cache Artist Images
            updateArtists();
        } catch (IOException e) {
            logger.warn("Couldn't fetch albums");
        } catch (CompletionException e) {
            logger.error("Album fetch task failed: {}", e.getMessage());
        } catch (Exception e) {
            logger.warn(e.getMessage());
        }
    }

    private static ArrayList<ArtistModel> resolveArtists(List<ShortArtist> artists) {
        var artistList = new ArrayList<ArtistModel>(3);
        for (var artist : artists) {
            var savedArtist = new QArtistModel().id.equalTo(artist.id()).findOne();
            if (savedArtist == null) {
                try {
                    var newArtist = new ArtistModel(artist.id(), artist.name());
                    newArtist.save();
                    artistList.add(newArtist);
                } catch (DuplicateKeyException e) {
                    savedArtist = new QArtistModel().id.equalTo(artist.id()).findOne();
                    artistList.add(savedArtist);
                }
            } else {
                artistList.add(savedArtist);
            }
        }
        return artistList;
    }

    public List<SongModel> getSongs(String albumId) {
        try {
            var songList = client.getSongsByAlbum(albumId);
            var songsSaved = new QSongModel().album.id.equalTo(albumId).findList();

            if (songsSaved.size() == songList.size()) {
                return songsSaved;
            }

            var futures = new ArrayList<CompletableFuture<SongModel>>(30);

            var album = new QAlbumModel().id.equalTo(albumId).findOne();
            for (var song : songList) {
                var future = CompletableFuture.supplyAsync(() -> {
                    var savedSongModel = new QSongModel().id.equalTo(song.id()).findOne();
                    var artists = resolveArtists(song.artists());
                    var newSongModel = new SongModel(
                            song.id(),
                            song.title(),
                            song.track(),
                            song.year(),
                            Duration.ofSeconds(song.duration()),
                            song.bitRate(),
                            song.contentType(),
                            song.discNumber(),
                            artists,
                            album
                    );

                    // Insert
                    if (savedSongModel == null) {
                        return newSongModel;
                    } else if (!newSongModel.equals(savedSongModel)) {
                        // Update
                        newSongModel.setId(savedSongModel.getId());
                        return newSongModel;
                    } else {
                        return savedSongModel;
                    }
                }, exec);
                futures.add(future);
            }

            var songModelList = futures.stream().map(CompletableFuture::join).toList();
            db.saveAll(songModelList);
            return songModelList;
        } catch (IOException e) {
            logger.warn("Couldn't retrieve songs for album: {}", albumId);
            return new ArrayList<>();
        } catch (Exception e) {
            logger.warn(e.getMessage());
            return new ArrayList<>();
        }
    }

    public HttpResponse<InputStream> seekSongStream(String songId, long position) throws IOException {
        return this.client.stream(songId, true, position);
    }

    public SubsonicResponse checkCredentials(String username) throws IOException {
        return this.client.userPing(username);
    }

    public SubsonicResponse ping() throws IOException {
        return this.client.ping();
    }

    public void setCredentials(String username, String password) {
        this.client.setUsername(username);
        this.client.setPassword(password);
    }

    public void newClient(String baseUrl, String username, String password) {
        var client = new SubsonicClient(baseUrl, username, password);
        logger.info("Reinitializing client for connecting to {}", client.getHost());
        this.client = client;
    }

    public boolean isClientInitialized() {
        return client != null;
    }

    public String getHost() {
        return client.getHost();
    }

    public String getScheme() {
        return client.getScheme();
    }

    public Subsonic() {
    }

    @Override
    public void close() {
        exec.close();
        threadExec.close();
    }
}
