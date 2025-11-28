package io.github.Nyeksenn.grooveboat.ui;

import io.ebean.DB;
import io.ebean.Database;
import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import org.gnome.gdk.MemoryTexture;
import org.gnome.gdk.Texture;
import org.javagi.base.GErrorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AlbumTextureLoader {
    private static final Logger logger = LoggerFactory.getLogger(AlbumTextureLoader.class);
    private final Map<String, Texture> map;
    private final Database db;

    private AlbumTextureLoader() {
        this.map = new ConcurrentHashMap<>();
        this.db = DB.getDefault();
    }

    // Holder class - loaded on first access to getInstance()
    private static class Holder {
        private static final AlbumTextureLoader INSTANCE = new AlbumTextureLoader();
    }

    public static AlbumTextureLoader getInstance() {
        return Holder.INSTANCE;
    }

    private Texture fetchTexture(String albumId) {
        var alb = QAlbumModel.alias();
        var album = new QAlbumModel().select(alb.albumId, alb.coverArt).albumId.equalTo(albumId).findOne();
        if (album != null) {
            try {
                return MemoryTexture.fromBytes(album.getCoverArt());
            } catch (GErrorException e) {
                logger.warn("Couldn't load texture for album {}: {}", album.getTitle(), e.getMessage());
                return null;
            }
        } else {
            return null;
        }
    }

    public Texture load(String albumId) {
        return map.computeIfAbsent(albumId, this::fetchTexture);
    }

    public Map<String, Texture> getMap() {
        return map;
    }
}
