package io.github.Nyeksenn.grooveboat.gobjects;

import org.gnome.gobject.GObject;
import org.gnome.gtk.Ordering;
import org.gnome.gtk.Sorter;
import org.gnome.gtk.SorterOrder;
import org.javagi.gobject.annotations.RegisteredType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;

@RegisteredType(name = "AlbumTitleSorter")
public class AlbumTitleSorter extends Sorter {
    private static final Logger logger = LoggerFactory.getLogger(AlbumTitleSorter.class);

    @Override
    public Ordering compare(GObject item1, GObject item2) {
        var first = (AlbumListItem) item1;
        var second = (AlbumListItem) item2;
        var ordering = first.getAlbumName().toLowerCase().compareTo(second.getAlbumName().toLowerCase());

        if (ordering < 0) {
            return Ordering.SMALLER;
        } else if (ordering == 0) {
            return Ordering.EQUAL;
        } else {
            return Ordering.LARGER;
        }
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
