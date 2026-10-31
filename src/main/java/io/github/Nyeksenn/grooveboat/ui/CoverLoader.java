package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.entities.query.QArtistModel;
import org.gnome.gdk.Texture;
import org.javagi.base.GErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide cache of decoded album covers and artist images. The image paths come from the database, where
 * {@code LibrarySync} stores them after caching the files. Albums and artists are kept apart, because a backend's
 * album and artist ids are not guaranteed to be distinct.
 */
public class CoverLoader {
    private static final Logger logger = LoggerFactory.getLogger(CoverLoader.class);
    private final Map<String, Texture> albums = new ConcurrentHashMap<>();
    private final Map<String, Texture> artists = new ConcurrentHashMap<>();

    private CoverLoader() {
    }

    // Holder class - loaded on first access to getInstance()
    private static class Holder {
        private static final CoverLoader INSTANCE = new CoverLoader();
    }

    public static CoverLoader getInstance() {
        return Holder.INSTANCE;
    }

    /** The album's cover, or {@code null} if it has none. */
    public Texture load(String albumId) {
        return albums.computeIfAbsent(albumId, id -> {
            var alb = QAlbumModel.alias();
            var album = new QAlbumModel().select(alb.id, alb.coverPath).id.equalTo(id).findOne();
            return album == null ? null : decode(album.getCoverPath(), "album " + id);
        });
    }

    /** The artist's image, or {@code null} if it has none. */
    public Texture loadArtist(String artistId) {
        return artists.computeIfAbsent(artistId, id -> {
            var art = QArtistModel.alias();
            var artist = new QArtistModel().select(art.id, art.artistImagePath).id.equalTo(id).findOne();
            return artist == null ? null : decode(artist.getArtistImagePath(), "artist " + id);
        });
    }

    private static Texture decode(Path path, String what) {
        if (path == null) {
            return null;
        }
        try {
            return Texture.fromFilename(path.toString());
        } catch (GErrorException e) {
            logger.warn("Couldn't load image for {}: {}", what, e.getMessage());
            return null;
        }
    }

    public void insert(String albumId, Texture texture) {
        albums.put(albumId, texture);
    }

    /** Drops all cached textures, e.g. on logout or after a sync replaced the images. */
    public void clear() {
        albums.clear();
        artists.clear();
    }

    /** Drops the cached artist images, so they are read again after an artist refresh. */
    public void clearArtists() {
        artists.clear();
    }
}
