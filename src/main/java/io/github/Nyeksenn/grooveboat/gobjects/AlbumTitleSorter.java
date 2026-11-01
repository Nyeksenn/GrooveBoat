package io.github.Nyeksenn.grooveboat.gobjects;

import org.gnome.gobject.GObject;
import org.gnome.gtk.Ordering;
import org.gnome.gtk.Sorter;
import org.gnome.gtk.SorterOrder;
import org.javagi.gobject.annotations.RegisteredType;
import java.lang.foreign.MemorySegment;
import java.util.Comparator;
import java.util.Locale;

@RegisteredType(name = "AlbumTitleSorter")
public class AlbumTitleSorter extends Sorter {
    /** Case-insensitive: by album artist, then by album name. */
    private static final Comparator<AlbumListItem> ORDER = Comparator
            .comparing((AlbumListItem item) -> item.getAlbumArtist().toLowerCase(Locale.ROOT))
            .thenComparing(item -> item.getAlbumName().toLowerCase(Locale.ROOT));

    @Override
    public Ordering compare(GObject item1, GObject item2) {
        var result = ORDER.compare((AlbumListItem) item1, (AlbumListItem) item2);
        return result < 0 ? Ordering.SMALLER : result == 0 ? Ordering.EQUAL : Ordering.LARGER;
    }

    @Override
    public SorterOrder getOrder() {
        return SorterOrder.PARTIAL;
    }

    public AlbumTitleSorter() {
    }

    public AlbumTitleSorter(MemorySegment address) {
        super(address);
    }
}
