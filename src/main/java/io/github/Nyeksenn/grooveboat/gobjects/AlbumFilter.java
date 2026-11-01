package io.github.Nyeksenn.grooveboat.gobjects;

import org.gnome.gobject.GObject;
import org.gnome.gtk.Filter;
import org.gnome.gtk.FilterChange;
import org.gnome.gtk.FilterMatch;
import org.javagi.gobject.annotations.RegisteredType;

import java.lang.foreign.MemorySegment;
import java.util.Locale;

@RegisteredType(name = "AlbumFilter")
public class AlbumFilter extends Filter {
    private String searchString = "";

    public String getSearchString() {
        return searchString;
    }

    public void setSearchString(String searchString) {
        this.searchString = searchString.toLowerCase(Locale.ROOT);
        emitChanged(FilterChange.DIFFERENT);
    }

    @Override
    public FilterMatch getStrictness() {
        return FilterMatch.SOME;
    }

    @Override
    public boolean match(GObject item) {
        var i = (AlbumListItem) item;
        var titleMatch = i.getAlbumName().toLowerCase(Locale.ROOT).contains(searchString);
        var artistMatch = i.getAlbumArtist().toLowerCase(Locale.ROOT).contains(searchString);
        return titleMatch || artistMatch;
    }

    public AlbumFilter() {
    }

    public AlbumFilter(String searchString) {
        this.searchString = searchString.toLowerCase(Locale.ROOT);
    }

    public AlbumFilter(MemorySegment address) {
        super(address);
    }
}
