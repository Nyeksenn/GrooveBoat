package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.gobjects.AlbumListItem;
import io.github.jwharm.javagi.base.GErrorException;
import io.github.jwharm.javagi.gobject.annotations.InstanceInit;
import io.github.jwharm.javagi.gobject.annotations.RegisteredType;
import io.github.jwharm.javagi.gtk.annotations.GtkCallback;
import io.github.jwharm.javagi.gtk.annotations.GtkChild;
import io.github.jwharm.javagi.gtk.annotations.GtkTemplate;
import org.gnome.gdk.MemoryTexture;
import org.gnome.gdk.Texture;
import org.gnome.gio.ListStore;
import org.gnome.glib.GLib;
import org.gnome.gobject.GObject;
import org.gnome.gtk.Box;
import org.gnome.gtk.GridView;
import org.gnome.gtk.ListItem;
import org.gnome.gtk.NoSelection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.ExecutorService;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/album-list.ui", name = "AlbumListView")
@RegisteredType(name = "AlbumListView")
public class AlbumListView extends Box {
    private static final Logger logger = LoggerFactory.getLogger(AlbumListView.class);
    private ListStore<AlbumListItem> store;
    private Subsonic subsonic;
    private ExecutorService executor;

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

        var album = (AlbumListItem) listItem.getItem();
        albumCard.albumPicture.setFromPaintable(album.getPictureData());
        albumCard.albumName.setText(album.getAlbumName());
    }

    public void initClient() {
        if (this.subsonic == null && this.executor == null) {
            var window = (GrooveboatWindow) getRoot();
            var app = (GrooveboatApp) window.getApplication();
            this.subsonic = app.subsonic;
            this.executor = app.executor;

            this.executor.submit(() -> {
                var albums = subsonic.getAlbums();
                for (var album : albums) {
                    Texture texture = null;
                    try {
                        texture = MemoryTexture.fromBytes(album.getCoverArt());
                    } catch (GErrorException e) {
                        logger.warn("Couldn't load texture");
                    }
                    var albumListItem = new AlbumListItem(texture, album.getTitle(), album.getArtist(), album.getAlbumId());
                    GLib.idleAddOnce(() -> {
                        this.store.append(albumListItem);
                    });
                }
            });
        }
    }

    @InstanceInit
    public void init() {
        this.onRealize(this::initClient);
        this.store = new ListStore<>();
        NoSelection<ListStore<AlbumListItem>> model = new NoSelection<>(this.store);
        gridView.setModel(model);
    }

    public AlbumListView() {
    }

    public AlbumListView(MemorySegment address) {
        super(address);
    }
}
