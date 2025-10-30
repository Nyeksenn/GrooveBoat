package io.github.Nyeksenn.grooveboat.client;

import io.ebean.DB;
import io.ebean.Database;
import io.github.Nyeksenn.grooveboat.deserializer.Album;
import io.github.Nyeksenn.grooveboat.entities.AlbumModel;
import io.github.Nyeksenn.grooveboat.entities.ArtistModel;
import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.entities.query.QArtistModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
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
                db.insertAll(artistModels);
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
                        List<ArtistModel> albumArtists = getAlbumArtists(album);
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

            db.insertAll(albumModels);
        } catch (IOException e) {
            logger.warn("Couldn't fetch albums");
        } catch (CompletionException e) {
            logger.error("Album fetch task failed: {}", e.getCause().toString());
        } catch (Exception e) {
            logger.warn(e.getCause().toString());
        }
    }

    private List<ArtistModel> getAlbumArtists(Album album) {
        List<ArtistModel> albumArtists = new ArrayList<>(5);
        for (var artist : album.artists()) {
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

    public InputStream getSongStream(String songId) {

        return null;
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
