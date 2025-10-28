package io.github.Nyeksenn.grooveboat.client;

import io.ebean.DB;
import io.ebean.Database;
import io.github.Nyeksenn.grooveboat.entities.AlbumModel;
import io.github.Nyeksenn.grooveboat.entities.ArtistModel;
import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.entities.query.QArtistModel;
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
        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<CompletableFuture<ArtistModel>>(100);
            try {
                var indices = client.getArtists();
                for (var index : indices) {
                    var artists = index.artist();
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
                }
            } catch (IOException e) {
                logger.warn("Couldn't refresh artists: {}", e.getCause().toString());
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
    }

    private void refreshAlbums() {
        var artists = new QArtistModel().id.asc().findList();

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<CompletableFuture<AlbumModel>>(100);
            for (var artist : artists) {
                var future = CompletableFuture.supplyAsync(() -> {
                    try {
                        var albums = client.getAlbumsByArtist(artist.getArtistId());
                        for (var album : albums) {
                            var albumArtist = new QArtistModel().artistId.equalTo(album.artistId()).findOne();

                            var savedAlbum = new QAlbumModel().albumId.equalTo(album.id()).findOne();
                            var coverArt = client.getCoverArt(album.id(), 500);
                            var newAlbum = new AlbumModel(
                                    album.id(),
                                    album.title(),
                                    album.songCount(),
                                    album.artist(),
                                    album.artistId(),
                                    album.year(),
                                    Duration.ofSeconds(album.duration()),
                                    coverArt,
                                    albumArtist
                            );
                            // Insert
                            if (savedAlbum == null) {
                                return newAlbum;
                            }
                            // Update
                            if (!newAlbum.equals(savedAlbum)) {
                                newAlbum.setId(savedAlbum.getId());
                                return newAlbum;
                            }
                        }
                    } catch (IOException e) {
                        logger.info("Couldn't fetch albums for artist");
                    }
                    return null;
                }, exec);
                futures.add(future);
            }

            var albumModels = futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull) // Filter out null results
                    .toList();

            db.insertAll(albumModels);
        } catch (CompletionException e) {
            logger.error("Album fetch task failed: {}", e.getCause().toString());
        } catch (Exception e) {
            logger.warn(e.getCause().toString());
        }
    }

    public List<ArtistModel> getArtists() {
        this.refreshArtists();

        return new QArtistModel().findList();
    }

    public List<AlbumModel> getAlbums() {
        this.refreshArtists();
        this.refreshAlbums();

        return new QAlbumModel().findList();
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
