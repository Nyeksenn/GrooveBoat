package io.github.Nyeksenn.grooveboat.gobjects;

import org.gnome.gdk.Texture;
import org.gnome.gobject.GObject;

public class AlbumListItem extends GObject {
    private final Texture pictureData;
    private final String albumName;
    private final String albumArtist;
    private final String albumId;

    public Texture getPictureData() {
        return pictureData;
    }

    public String getAlbumName() {
        return albumName;
    }

    public String getAlbumArtist() {
        return albumArtist;
    }

    public String getAlbumId() {
        return albumId;
    }

    public AlbumListItem(Texture pictureData, String albumName, String albumArtist, String id) {
        this.pictureData = pictureData;
        this.albumName = albumName;
        this.albumArtist = albumArtist;
        this.albumId = id;
    }
}
