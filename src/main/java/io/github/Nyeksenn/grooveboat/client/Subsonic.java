package io.github.Nyeksenn.grooveboat.client;

import io.ebean.DB;
import io.ebean.Database;
import io.github.Nyeksenn.grooveboat.entities.ArtistModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Subsonic implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(Subsonic.class);

    public SubsonicClient client;
    private Database db;

    public void refreshArtists() {
        var delete = db.sqlUpdate("TRUNCATE TABLE artist");
        delete.execute();

        try {
            var indices = client.getArtists();

            try (var transaction = db.beginTransaction()) {
                transaction.setBatchMode(true);
                transaction.setBatchSize(100);
                transaction.setGetGeneratedKeys(false);

                for (var index : indices.index) {
                    var artists = index.artist;
                    for (var artist : artists) {
                        try {
                            var artistImage = client.getArtistImage(artist.getArtistImageUrl());
                            var artistModel = new ArtistModel(artist.getId(), artist.getName(), artistImage);
                            artistModel.save();
                        } catch (IOException e) {
                            logger.warn("Couldn't fetch artist image for: {}", artist.getName());
                            var artistModel = new ArtistModel(artist.getId(), artist.getName(), null);
                            artistModel.save();
                        }
                    }
                }
                transaction.commit();
            }
        } catch (IOException e) {
            logger.warn("Couldn't refresh artists: {}", e.getCause().toString());
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
