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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Subsonic implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(Subsonic.class);

    public SubsonicClient client;
    private final Database db;
    private final ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();

    private List<ArtistModel> refreshArtists() {
        try {
            var numSavedArtists = new QArtistModel().findCount();
            var artists = client.getArtists();
            var artistModelList = new ArrayList<ArtistModel>(100);

            if (numSavedArtists == artists.size()) {
                return new QArtistModel().findList();
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
            List<CompletableFuture<AlbumModel>> futures = new ArrayList<>(200);

            if (numSavedAlbums == albums.size()) {
                return new QAlbumModel().findList();
            }

            for (var album : albums) {
                var future = CompletableFuture.supplyAsync(() -> {
                    var savedAlbumModel = new QAlbumModel().albumId.equalTo(album.id()).findOne();

                    var albumArtists = resolveArtists(album.artists());
                    var albumCover = client.getCoverArt(album.coverArt(), 500);

                    var newAlbumModel = new AlbumModel(album.id(), album.name(), album.songCount(), album.year(), Duration.ofSeconds(album.duration()), albumCover, albumArtists);

                    // Insert
                    if (savedAlbumModel == null) {
                        return newAlbumModel;
                    } else if (!newAlbumModel.equals(savedAlbumModel)) {
                        // Update
                        newAlbumModel.setId(savedAlbumModel.getId());
                        return newAlbumModel;
                    } else {
                        return savedAlbumModel;
                    }
                }, exec);
                futures.add(future);
            }

            var albumModelList = futures.stream().map(CompletableFuture::join).toList();
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

    private static ArrayList<ArtistModel> resolveArtists(List<ShortArtist> artists) {
        var artistList = new ArrayList<ArtistModel>(3);
        for (var artist : artists) {
            var savedArtist = new QArtistModel().artistId.equalTo(artist.id()).findOne();
            if (savedArtist == null) {
                try {
                    var newArtist = new ArtistModel(artist.id(), artist.name());
                    newArtist.save();
                    artistList.add(newArtist);
                } catch (DuplicateKeyException e) {
                    savedArtist = new QArtistModel().artistId.equalTo(artist.id()).findOne();
                    artistList.add(savedArtist);
                }
            } else {
                artistList.add(savedArtist);
            }
        }
        return artistList;
    }


    public List<AlbumModel> getAlbums() {
        return this.refreshAlbums();
    }

    public List<ArtistModel> getArtists() {
        return this.refreshArtists();
    }

    public List<SongModel> getSongs(String albumId) {
        try {
            var songList = client.getSongsByAlbum(albumId);
            var songsSaved = new QSongModel().album.albumId.equalTo(albumId).findList();

            if (songsSaved.size() == songList.size()) {
                return songsSaved;
            }

            var futures = new ArrayList<CompletableFuture<SongModel>>(30);

            var album = new QAlbumModel().albumId.equalTo(albumId).findOne();
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
            logger.warn(e.getCause().toString());
            return new ArrayList<>();
        }
    }

    public HttpResponse<InputStream> getSongStream(String songId) {
        return this.client.stream(songId, false, 0L);
    }

    public HttpResponse<InputStream> seekSongStream(String songId, long position) {
        return this.client.stream(songId, true, position);
    }

    public SubsonicResponse checkCredentials(String username) throws IOException {
        return this.client.userPing(username);
    }

    public Subsonic(SubsonicClient client) {
        logger.info("Connecting to {}", client.getHost());
        this.client = client;
        this.db = DB.getDefault();
    }

    @Override
    public void close() {
        exec.close();
        client.close();
    }
}
