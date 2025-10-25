package io.github.Nyeksenn.grooveboat.ui;

import io.github.jwharm.javagi.gobject.annotations.RegisteredType;
import io.github.jwharm.javagi.gtk.annotations.GtkCallback;
import io.github.jwharm.javagi.gtk.annotations.GtkTemplate;
import org.gnome.gio.MenuModel;
import org.gnome.gobject.GObject;
import org.gnome.gtk.*;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/album-list.ui", name = "AlbumListView")
@RegisteredType(name = "AlbumListView")
public class AlbumListView extends Box {
    @GtkCallback(name = "album-grid-setup")
    public void albumGridSetup(GObject object) {
        var listItem = (ListItem) object;
        var builder = GtkBuilder.fromResource("/io/github/Nyeksenn/grooveboat/album-card.ui");
        var box = (Box) builder.getObject("box");
        listItem.setChild(box);
    }

    @GtkCallback(name = "album-grid-bind")
    public void albumGridBind(GObject object) {
        var listItem = (ListItem) object;
        var box = (Box) listItem.getChild();
        var picture = (Picture) box.getFirstChild();
        var label = (Label) picture.getNextSibling();
    }

    public AlbumListView() {}

    public AlbumListView(MemorySegment address) {
        super(address);
    }
}
