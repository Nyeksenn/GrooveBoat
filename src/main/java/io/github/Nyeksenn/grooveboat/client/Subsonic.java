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

public class Subsonic implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(Subsonic.class);

    public SubsonicClient client;
    private final Database db;

    public void refreshArtists() {
        try {
            var indices = client.getArtists();
            var savedArtists = new QArtistModel().findList();

            try (var trx = db.beginTransaction()) {
                trx.setBatchMode(true);
                trx.setBatchSize(100);

                for (var index : indices.index) {
                    var artists = index.artist;
                    for (var artist : artists) {
                        var savedArtistModel = new QArtistModel().artistId.equalTo(artist.getId()).findOne();
                        var artistImage = client.getArtistImage(artist.getArtistImageUrl());
                        var newArtistModel = new ArtistModel(artist.getId(), artist.getName(), artistImage);

                        // Insert
                        if (savedArtistModel == null) {
                            logger.info("Inserting new artist");
                            newArtistModel.save();
                        }

                        // Update
                        if (!newArtistModel.equals(savedArtistModel) && savedArtistModel != null) {
                            newArtistModel.setId(savedArtistModel.getId());
                            newArtistModel.save();
                        }
                    }
                }
                trx.commit();
            }
        } catch (IOException e) {
            logger.warn("Couldn't refresh artists: {}", e.getCause().toString());
        }
    }

    public void refreshAlbums() {
        var artists = new QArtistModel().id.asc().findList();
        try (var trx = db.beginTransaction()) {
            trx.setBatchMode(true);
            trx.setBatchSize(100);

            try {
                for (var artist : artists) {
                    try {
                        var albums = client.getAlbumsByArtist(artist.getArtistId());
                        for (var album : albums.child) {
                            var albumArtist = new QArtistModel().artistId.equalTo(album.getArtistId()).findOne();

                            var savedAlbum = new QAlbumModel().albumId.equalTo(album.getId()).findOne();
                            var coverArt = client.getCoverArt(album.getId());
                            var newAlbum = new AlbumModel(
                                    album.getId(),
                                    album.getTitle(),
                                    album.getSongCount(),
                                    album.getArtist(),
                                    album.getArtistId(),
                                    album.getYear(),
                                    Duration.ofSeconds(album.getDuration()),
                                    coverArt,
                                    albumArtist
                            );

                            // Update
                            if (!newAlbum.equals(savedAlbum) && savedAlbum != null) {
                                newAlbum.setId(savedAlbum.getId());
                                newAlbum.save();
                            }
                            // Insert
                            if (savedAlbum == null) {
                                newAlbum.save();
                            }
                        }
                    } catch (IOException e) {
                        logger.info("Couldn't fetch albums for artist");
                    }
                }
            } catch (Exception e) {
                logger.warn(e.getCause().toString());
            }

            trx.commit();
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
