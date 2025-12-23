package io.github.Nyeksenn.grooveboat.gobjects;

import org.gnome.gdk.Texture;
import org.gnome.gobject.GObject;
import org.javagi.gobject.annotations.Property;
import org.javagi.gobject.annotations.RegisteredType;

import java.lang.foreign.MemorySegment;

@RegisteredType(name = "AlbumListItem")
public final class AlbumListItem extends GObject {
    private Texture pictureData;
    private String albumName;
    private String albumArtist;
    private String albumId;

    public Texture getPictureData() {
        return pictureData;
    }

    @Property(name = "album_name", writable = false)
    public String getAlbumName() {
        return albumName;
    }

    public String getAlbumArtist() {
        return albumArtist;
    }

    public String getAlbumId() {
        return albumId;
    }

    public AlbumListItem() {
    }

    public AlbumListItem(MemorySegment address) {
        super(address);
    }

    public AlbumListItem(Texture pictureData, String albumName, String albumArtist, String id) {
        this.pictureData = pictureData;
        this.albumName = albumName;
        this.albumArtist = albumArtist;
        this.albumId = id;
    }
}
