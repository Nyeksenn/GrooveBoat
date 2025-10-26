package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.gobjects.AlbumListItem;
import io.github.jwharm.javagi.gobject.annotations.InstanceInit;
import io.github.jwharm.javagi.gobject.annotations.RegisteredType;
import io.github.jwharm.javagi.gtk.annotations.GtkCallback;
import io.github.jwharm.javagi.gtk.annotations.GtkChild;
import io.github.jwharm.javagi.gtk.annotations.GtkTemplate;
import org.gnome.gio.ListStore;
import org.gnome.gio.MenuModel;
import org.gnome.gobject.GObject;
import org.gnome.gtk.*;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/album-list.ui", name = "AlbumListView")
@RegisteredType(name = "AlbumListView")
public class AlbumListView extends Box {
    public ListStore<AlbumListItem> listStore;

    @GtkChild(name = "album-grid-view")
    public GridView gridView;

    @GtkCallback(name = "album-grid-setup")
    public void albumGridSetup(GObject object) {
        var listItem = (ListItem) object;
        var albumCard = new AlbumCard();
        listItem.setChild(albumCard);
    }

    @GtkCallback(name = "album-grid-bind")
    public void albumGridBind(GObject object) {
        var listItem = (ListItem) object;
        var albumCard = (AlbumCard) listItem.getChild();
    }

    @InstanceInit
    public void init() {
        this.listStore = new ListStore<>();
        var model = new SingleSelection<ListStore<AlbumListItem>>(this.listStore);
        gridView.setModel(model);
    }

    public AlbumListView() {}

    public AlbumListView(MemorySegment address) {
        super(address);
    }
}
