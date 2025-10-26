package io.github.Nyeksenn.grooveboat.gobjects;

import org.gnome.gobject.GObject;

public class AlbumListItem extends GObject {
    private final byte[] pictureData;
    private final String albumName;
    private final String albumArtist;
    private final String id;

    public byte[] getPictureData() {
        return pictureData;
    }

    public String getAlbumName() {
        return albumName;
    }

    public String getAlbumArtist() {
        return albumArtist;
    }

    public String getId() {
        return id;
    }

    public AlbumListItem(byte[] pictureData, String albumName, String albumArtist, String id) {
        this.pictureData = pictureData;
        this.albumName = albumName;
        this.albumArtist = albumArtist;
        this.id = id;
    }
}
