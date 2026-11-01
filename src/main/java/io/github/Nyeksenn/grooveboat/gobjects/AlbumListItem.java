package io.github.Nyeksenn.grooveboat.gobjects;

import io.github.Nyeksenn.grooveboat.entities.AlbumModel;
import org.gnome.gdk.Texture;
import org.gnome.gobject.GObject;
import org.javagi.gobject.annotations.Property;
import org.javagi.gobject.annotations.RegisteredType;
import org.jspecify.annotations.Nullable;

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

    /** A grid entry for {@code album}. {@code cover} is its decoded cover, if any. */
    public static AlbumListItem of(AlbumModel album, @Nullable Texture cover) {
        return new AlbumListItem(cover, album.getTitle(), album.getDisplayArtist(), album.getId());
    }

    private AlbumListItem(Texture pictureData, String albumName, String albumArtist, String id) {
        this.pictureData = pictureData;
        this.albumName = albumName;
        this.albumArtist = albumArtist;
        this.albumId = id;
    }
}
