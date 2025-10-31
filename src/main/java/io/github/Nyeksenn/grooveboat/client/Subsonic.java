package io.github.Nyeksenn.grooveboat.client;

import io.ebean.DB;
import io.ebean.Database;
import io.github.Nyeksenn.grooveboat.deserializer.ShortArtist;
import io.github.Nyeksenn.grooveboat.entities.AlbumModel;
import io.github.Nyeksenn.grooveboat.entities.ArtistModel;
import io.github.Nyeksenn.grooveboat.entities.SongModel;
import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.entities.query.QArtistModel;
import io.github.Nyeksenn.grooveboat.entities.query.QSongModel;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;

public class Subsonic implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(Subsonic.class);

    public SubsonicClient client;
    private Database db;

    private void refreshArtists() {
        try {
            var numSavedArtists = new QArtistModel().findCount();
            var artists = client.getArtists();

            if (numSavedArtists == artists.size()) {
                return;
            }

            try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = new ArrayList<CompletableFuture<ArtistModel>>(100);
                for (var artist : artists) {
                    var future = CompletableFuture.supplyAsync(() -> {
                        var savedArtistModel = new QArtistModel().artistId.equalTo(artist.id()).findOne();
                        var artistImage = client.getArtistImage(artist.artistImageUrl());
                        var newArtistModel = new ArtistModel(artist.id(), artist.name(), artistImage);

                        // Insert
                        if (savedArtistModel == null) {
                            return newArtistModel;
                        }

                        // Update
                        if (!newArtistModel.equals(savedArtistModel)) {
                            newArtistModel.setId(savedArtistModel.getId());
                            return newArtistModel;
                        }

                        return null;
                    }, exec);
                    futures.add(future);
                }
                var artistModels = futures.stream()
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull) // Filter out null results
                        .toList();
                db.saveAll(artistModels);
            } catch (CompletionException e) {
                logger.error("Artist fetch task failed: {}", e.getCause().toString());
            } catch (Exception e) {
                logger.warn(e.getCause().toString());
            }
        } catch (IOException e) {
            logger.warn("Couldn't fetch artist list: {}", e.getCause().toString());
        }
    }

    private void refreshAlbums() {
        try {
            var albums = client.getAlbumList2();
            var numSavedAlbums = new QAlbumModel().findCount();

            if (numSavedAlbums == albums.size()) {
                return;
            }

            var futures = new ArrayList<CompletableFuture<AlbumModel>>(200);

            try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
                for (var album : albums) {
                    var future = CompletableFuture.supplyAsync(() -> {
                        List<ArtistModel> albumArtists = resolveArtists(album.artists());
                        var savedAlbum = new QAlbumModel().albumId.equalTo(album.id()).findOne();

                        var albumCover = client.getCoverArt(album.coverArt(), 500);
                        var newAlbum = new AlbumModel(album.id(), album.name(), album.songCount(), album.year(), Duration.ofSeconds(album.duration()), albumCover, albumArtists);

                        // Insert
                        if (savedAlbum == null) {
                            return newAlbum;
                        }
                        // Update
                        if (!newAlbum.equals(savedAlbum)) {
                            newAlbum.setId(savedAlbum.getId());
                            return newAlbum;
                        }

                        return null;
                    }, exec);

                    futures.add(future);
                }
            }

            var albumModels = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull) // Filter out null results
                    .toList();

            db.saveAll(albumModels);
        } catch (IOException e) {
            logger.warn("Couldn't fetch albums");
        } catch (CompletionException e) {
            logger.error("Album fetch task failed: {}", e.getCause().toString());
        } catch (Exception e) {
            logger.warn(e.getCause().toString());
        }
    }

    private List<ArtistModel> resolveArtists(List<ShortArtist> artists) {
        List<ArtistModel> albumArtists = new ArrayList<>(5);
        for (var artist : artists) {
            try {
                var albumArtist = client.getArtist(artist.id());

                var savedArtist = new QArtistModel().artistId.equalTo(albumArtist.id()).findOne();
                if (savedArtist != null) {
                    albumArtists.add(savedArtist);
                } else {
                    var artistImage = client.getArtistImage(albumArtist.artistImageUrl());
                    var newArtist = new ArtistModel(albumArtist.id(), albumArtist.name(), artistImage);
                    newArtist.save();
                    albumArtists.add(newArtist);
                }
            } catch (IOException e) {
                logger.warn("Couldn't get artist {}", artist.id());
            }
        }
        return albumArtists;
    }

    public List<AlbumModel> getAlbums() {
        this.refreshArtists();
        this.refreshAlbums();

        return new QAlbumModel().findList();
    }

    public List<SongModel> getSongs(String albumId) {
        try {
            var songList = client.getSongsByAlbum(albumId);
            var songsSaved = new QSongModel().album.albumId.equalTo(albumId).findList();

            if (songsSaved.size() == songList.size()) {
                return songsSaved;
            }

            var album = new QAlbumModel().albumId.equalTo(albumId).findOne();

            try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
                var futures = new ArrayList<CompletableFuture<SongModel>>(20);
                for (var song : songList) {
                    var future = CompletableFuture.supplyAsync(() -> {
                        var savedSongModel = new QSongModel().songId.equalTo(song.id()).findOne();
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
                        }

                        // Update
                        if (!newSongModel.equals(savedSongModel)) {
                            newSongModel.setId(savedSongModel.getId());
                            return newSongModel;
                        }

                        return null;
                    }, exec);

                    futures.add(future);
                }
                var songModels = futures.stream()
                        .map(CompletableFuture::join)
                        .filter(Objects::nonNull)
                        .toList();

                db.saveAll(songModels);
                return songModels;
            }
        } catch (IOException e) {
            logger.warn("Couldn't retrieve songs for album: {}", albumId);
            return new ArrayList<>();
        } catch (CompletionException e) {
            logger.error("Song fetch task failed: {}", e.getCause().toString());
            return new ArrayList<>();
        } catch (Exception e) {
            logger.warn(e.getCause().toString());
            return new ArrayList<>();
        }
    }

    public ClassicHttpResponse getSongStream(String songId) {
        try {
            return this.client.stream(songId);
        } catch (IOException e) {
            logger.warn("Couldn't get stream for song {}", songId);
            return null;
        }
    }

    public Subsonic(SubsonicClient client) {
        logger.info("Connecting to {}", client.getBaseUrl());
        this.client = client;
        this.db = DB.getDefault();
    }

    @Override
    public void close() throws Exception {
        client.close();
    }
}
