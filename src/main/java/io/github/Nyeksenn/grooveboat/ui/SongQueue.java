package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import org.gnome.adw.Bin;
import org.gnome.gio.ListStore;
import org.gnome.gobject.GObject;
import org.gnome.gobject.ParamSpec;
import org.gnome.gtk.*;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.Set;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/song-queue.ui", name = "SongQueue")
public final class SongQueue extends Bin {
    private static final Logger logger = LoggerFactory.getLogger(SongQueue.class);
    private SingleSelection<ListStore<SongListItem>> currentSelection;

    @GtkChild(name = "queue-scroll-window")
    public ScrolledWindow queueScrollWindow;

    @GtkChild(name = "queue-list-view")
    public ListView queueListView;

    @GtkCallback(name = "queue-list-setup")
    public void queueListSetup(GObject object) {
        var listItem = (ListItem) object;
        var queueCard = new QueueCard();
        listItem.setChild(queueCard);
    }

    @GtkCallback(name = "queue-list-bind")
    public void queueListBind(GObject object) {
        var listItem = (ListItem) object;
        var queueCard = (QueueCard) listItem.getChild();
        var songItem = (SongListItem) listItem.getItem();
        queueCard.albumCover.setTexture(songItem.getCoverArt());
        queueCard.albumArtistLabel.setLabel(songItem.getArtistName());
        queueCard.songTitleLabel.setLabel(songItem.getSongName());
        listItem.bindProperty("position", queueCard, "index").syncCreate().build();
    }

    public void itemSelectionChanged(ParamSpec paramSpec) {
        queueListView.scrollTo(currentSelection.getSelected(), Set.of(ListScrollFlags.FOCUS), null);
    }

    @InstanceInit
    public void init() {
        onRealize(() -> {
            var win = (GrooveboatWindow) getRoot();
            assert win != null;
            var app = (GrooveboatApp) win.getApplication();
            this.currentSelection = app.queueStore.getSelection();
            queueListView.setModel(this.currentSelection);
            this.currentSelection.onNotify("selected", this::itemSelectionChanged);
        });
    }

    public SongQueue() {
    }

    public SongQueue(MemorySegment address) {
        super(address);
    }
}
