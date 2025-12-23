package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.gobjects.AlbumListItem;
import org.gnome.adw.NavigationView;
import org.gnome.gio.ListStore;
import org.gnome.glib.GLib;
import org.gnome.gobject.GObject;
import org.gnome.gtk.Box;
import org.gnome.gtk.GridView;
import org.gnome.gtk.ListItem;
import org.gnome.gtk.NoSelection;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/album-list.ui", name = "AlbumListView")
@RegisteredType(name = "AlbumListView")
public class AlbumListView extends Box {
    private static final Logger logger = LoggerFactory.getLogger(AlbumListView.class);
    private ListStore<AlbumListItem> store;
    private Subsonic subsonic;
    private ExecutorService executor;

    @GtkChild(name = "album-grid-view")
    public GridView gridView;

    @GtkChild(name = "album-nav-view")
    public NavigationView albumNavView;

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

        albumCard.albumPicture.setTexture(album.getPictureData());
        albumCard.albumName.setLabel(album.getAlbumName());
        albumCard.albumArtist.setLabel(album.getAlbumArtist());
        albumCard.setAlbumId(album.getAlbumId());
        albumCard.navView = albumNavView;
    }

    public void loadItems() {
        if (this.subsonic == null && this.executor == null) {
            var window = (GrooveboatWindow) getRoot();
            var albumTextureLoader = AlbumTextureLoader.getInstance();
            var app = (GrooveboatApp) window.getApplication();

            this.subsonic = app.subsonic;
            this.executor = app.executor;

            this.executor.submit(() -> {
                var albums = subsonic.getAlbums();
                if (albums != null) {
                    for (var album : albums) {
                        try (var localExec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
                            localExec.submit(() -> {
                                try {
                                    var texture = albumTextureLoader.load(album.getAlbumId());
                                    var albumListItem = new AlbumListItem(texture, album.getTitle(), album.getDisplayArtist(), album.getAlbumId());
                                    GLib.idleAddOnce(() -> this.store.append(albumListItem));
                                } catch (NullPointerException e) {
                                    logger.warn("Didn't find texture for album {}", album.getTitle());
                                }
                            });
                        }
                    }
                }
            });
        }
    }

    @InstanceInit
    public void init() {
        this.store = new ListStore<>();
        NoSelection<AlbumListItem> model = new NoSelection<>(this.store);
        gridView.setModel(model);
        this.onRealize(this::loadItems);
    }

    public AlbumListView() {
    }

    public AlbumListView(MemorySegment address) {
        super(address);
    }
}
