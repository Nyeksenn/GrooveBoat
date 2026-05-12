package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.gobjects.AlbumFilter;
import io.github.Nyeksenn.grooveboat.gobjects.AlbumListItem;
import io.github.Nyeksenn.grooveboat.gobjects.AlbumTitleSorter;
import org.gnome.adw.NavigationView;
import org.gnome.gio.ListStore;
import org.gnome.glib.GLib;
import org.gnome.gobject.GObject;
import org.gnome.gtk.*;
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
    private final AlbumTitleSorter titleSorter = new AlbumTitleSorter();
    private final AlbumFilter filter = new AlbumFilter();
    private ListStore<AlbumListItem> store;
    private SortListModel<AlbumListItem> sortedModel;
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
                var albums = new QAlbumModel().findList();
                for (var album : albums) {
                    try (var localExec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
                        localExec.submit(() -> {
                            try {
                                var texture = albumTextureLoader.load(album.getId());
                                var albumListItem = new AlbumListItem(texture, album.getTitle(), album.getDisplayArtist(), album.getId());
                                GLib.idleAddOnce(() -> this.store.append(albumListItem));
                            } catch (NullPointerException e) {
                                logger.warn("Didn't find texture for album {}", album.getTitle());
                            }
                        });
                    }
                }
            });
        }
    }

    @InstanceInit
    public void init() {
        this.store = new ListStore<>();
        this.sortedModel = new SortListModel<>(this.store, titleSorter);
        gridView.setModel(new NoSelection<AlbumListItem>(this.sortedModel));
        this.onRealize(() -> {
            var win = (GrooveboatWindow) getRoot();
            win.musicChooser.searchEntry.onSearchStarted(() -> {
                gridView.setModel(new NoSelection<AlbumListItem>(new FilterListModel<AlbumListItem>(sortedModel, filter)));
            });
            win.musicChooser.searchEntry.onStopSearch(() -> {
                gridView.setModel(new NoSelection<AlbumListItem>(sortedModel));
            });
            win.musicChooser.searchEntry.onSearchChanged(() -> {
                var searchTerm = win.musicChooser.searchEntry.getText();
                filter.setSearchString(searchTerm);
            });
            loadItems();
        });
    }

    public AlbumListView() {
    }

    public AlbumListView(MemorySegment address) {
        super(address);
    }
}
