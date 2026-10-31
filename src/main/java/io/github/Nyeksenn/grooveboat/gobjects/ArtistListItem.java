package io.github.Nyeksenn.grooveboat.gobjects;

import org.gnome.gdk.Texture;
import org.gnome.gobject.GObject;
import org.javagi.gobject.annotations.Property;
import org.javagi.gobject.annotations.RegisteredType;

import java.lang.foreign.MemorySegment;

@RegisteredType(name = "ArtistListItem")
public final class ArtistListItem extends GObject {
    private Texture coverArt;
    private String artistName;
    private String artistId;

    public String getArtistId() {
        return artistId;
    }

    @Property(name = "artist_name", writable = false)
    public String getArtistName() {
        return artistName;
    }

    public Texture getCoverArt() {
        return coverArt;
    }

    public ArtistListItem() {}

    public ArtistListItem(MemorySegment address) {
        super(address);
    }

    public ArtistListItem(Texture coverArt, String artistName, String artistId) {
        this.coverArt = coverArt;
        this.artistName = artistName;
        this.artistId = artistId;
    }
}
