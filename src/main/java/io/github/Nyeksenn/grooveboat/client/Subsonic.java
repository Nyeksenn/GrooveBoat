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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

public class Subsonic implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(Subsonic.class);

    public SubsonicClient client;
    private final Database db;

    private List<ArtistModel> refreshArtists() {
        try {
            var numSavedArtists = new QArtistModel().findCount();
            var artists = client.getArtists();
            var artistModelList = new ArrayList<ArtistModel>(100);

            if (numSavedArtists == artists.size()) {
                return new QArtistModel().findList() ;
            }

            try {
                for (var artist : artists) {
                    var savedArtistModel = new QArtistModel().artistId.equalTo(artist.id()).findOne();
                    var artistImage = client.getArtistImage(artist.artistImageUrl());
                    var newArtistModel = new ArtistModel(artist.id(), artist.name(), artistImage);

                    // Insert
                    if (savedArtistModel == null) {
                        artistModelList.add(newArtistModel);
                    } else if (!newArtistModel.equals(savedArtistModel)) {
                        // Update
                        newArtistModel.setId(savedArtistModel.getId());
                        artistModelList.add(newArtistModel);
                    } else {
                        artistModelList.add(savedArtistModel);
                    }

                }

                db.saveAll(artistModelList);
                return artistModelList;
            } catch (Exception e) {
                logger.warn(e.getCause().toString());
            }
        } catch (IOException e) {
            logger.warn("Couldn't fetch artist list: {}", e.getCause().toString());
        }

        return null;
    }

    private List<AlbumModel> refreshAlbums() {
        try {
            var albums = client.getAlbumList2();
            var numSavedAlbums = new QAlbumModel().findCount();
            var albumModelList = new ArrayList<AlbumModel>(200);

            if (numSavedAlbums == albums.size()) {
                return new QAlbumModel().findList();
            }

            for (var album : albums) {
                List<ArtistModel> albumArtists = resolveArtists(album.artists());
                var savedAlbumModel = new QAlbumModel().albumId.equalTo(album.id()).findOne();

                var albumCover = client.getCoverArt(album.coverArt(), 500);
                var newAlbumModel = new AlbumModel(album.id(), album.name(), album.songCount(), album.year(), Duration.ofSeconds(album.duration()), albumCover, albumArtists);

                // Insert
                if (savedAlbumModel == null) {
                    albumModelList.add(newAlbumModel);
                } else if (!newAlbumModel.equals(savedAlbumModel)) {
                    // Update
                    newAlbumModel.setId(savedAlbumModel.getId());
                    albumModelList.add(newAlbumModel);
                } else {
                    albumModelList.add(savedAlbumModel);
                }
            }

            db.saveAll(albumModelList);
            return albumModelList;
        } catch (IOException e) {
            logger.warn("Couldn't fetch albums");
        } catch (CompletionException e) {
            logger.error("Album fetch task failed: {}", e.getMessage());
        } catch (Exception e) {
            logger.warn(e.getMessage());
        }

        return null;
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
        return this.refreshAlbums();
    }

    public List<ArtistModel> getArtists() {
        return this.refreshArtists();
    }

    public List<SongModel> getSongs(String albumId) {
        try {
            var songList = client.getSongsByAlbum(albumId);
            var songsSaved = new QSongModel().album.albumId.equalTo(albumId).findList();
            var songModelList = new ArrayList<SongModel>(20);

            if (songsSaved.size() == songList.size()) {
                return songsSaved;
            }

            var album = new QAlbumModel().albumId.equalTo(albumId).findOne();
            for (var song : songList) {
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
                    songModelList.add(newSongModel);
                } else if (!newSongModel.equals(savedSongModel)) {
                    // Update
                    newSongModel.setId(savedSongModel.getId());
                    songModelList.add(newSongModel);
                } else {
                    songModelList.add(savedSongModel);
                }
            }

            db.saveAll(songModelList);
            return songModelList;
        } catch (IOException e) {
            logger.warn("Couldn't retrieve songs for album: {}", albumId);
            return new ArrayList<>();
        } catch (Exception e) {
            logger.warn(e.getCause().toString());
            return new ArrayList<>();
        }
    }

    public HttpResponse<InputStream> getSongStream(String songId) {
        try {
            return this.client.stream(songId);
        } catch (IOException e) {
            logger.warn("Couldn't get stream for song {}", songId);
            return null;
        }
    }

    public Subsonic(SubsonicClient client) {
        logger.info("Connecting to {}", client.getHost());
        this.client = client;
        this.db = DB.getDefault();
    }

    @Override
    public void close() {
        client.close();
    }
}
