package io.github.Nyeksenn.grooveboat.gobjects;

import org.gnome.gobject.GObject;
import org.gnome.gtk.Filter;
import org.gnome.gtk.FilterChange;
import org.gnome.gtk.FilterMatch;
import org.javagi.gobject.annotations.RegisteredType;

import java.lang.foreign.MemorySegment;

@RegisteredType(name = "AlbumFilter")
public class AlbumFilter extends Filter {
    private String searchString = "";

    public String getSearchString() {
        return searchString;
    }

    public void setSearchString(String searchString) {
        this.searchString = searchString.toLowerCase();
        emitChanged(FilterChange.DIFFERENT);
    }

    @Override
    public FilterMatch getStrictness() {
        return FilterMatch.SOME;
    }

    @Override
    public boolean match(GObject item) {
        var i = (AlbumListItem) item;
        var titleMatch = i.getAlbumName().toLowerCase().contains(searchString);
        var artistMatch = i.getAlbumArtist().toLowerCase().contains(searchString);
        return titleMatch || artistMatch;
    }

    public AlbumFilter() {
    }

    public AlbumFilter(String searchString) {
        this.searchString = searchString.toLowerCase();
    }

    public AlbumFilter(MemorySegment address) {
        super(address);
    }
}
