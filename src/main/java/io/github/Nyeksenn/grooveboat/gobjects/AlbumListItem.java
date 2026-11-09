package io.github.Nyeksenn.grooveboat.gobjects;

import io.github.Nyeksenn.grooveboat.entities.AlbumModel;
import org.gnome.gobject.GObject;
import org.javagi.gobject.annotations.Property;
import org.javagi.gobject.annotations.RegisteredType;

import java.lang.foreign.MemorySegment;

@RegisteredType(name = "AlbumListItem")
public final class AlbumListItem extends GObject {
    private String albumName;
    private String albumArtist;
    private String albumId;

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

    /** A grid entry for {@code album}. Its cover is loaded by the widget that shows it, at the size it needs. */
    public static AlbumListItem of(AlbumModel album) {
        return new AlbumListItem(album.getTitle(), album.getDisplayArtist(), album.getId());
    }

    private AlbumListItem(String albumName, String albumArtist, String id) {
        this.albumName = albumName;
        this.albumArtist = albumArtist;
        this.albumId = id;
    }
}
