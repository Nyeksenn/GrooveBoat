package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.gobjects.AlbumFilter;
import io.github.Nyeksenn.grooveboat.gobjects.AlbumListItem;
import io.github.Nyeksenn.grooveboat.gobjects.AlbumTitleSorter;
import org.gnome.adw.NavigationView;
import org.gnome.gio.ListStore;
import org.gnome.gio.SimpleAction;
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
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/album-list.ui", name = "AlbumListView")
@RegisteredType(name = "AlbumListView")
public class AlbumListView extends Box {
    private static final Logger logger = LoggerFactory.getLogger(AlbumListView.class);
    private final AlbumTitleSorter titleSorter = new AlbumTitleSorter();
    private final AlbumFilter filter = new AlbumFilter();
    private ListStore<AlbumListItem> store;
    private SortListModel<AlbumListItem> sortedModel;
    private ExecutorService executor;
    /** Only the newest load is applied, so overlapping reloads can't add albums twice. */
    private final UiAsync.Latest latest = new UiAsync.Latest();

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

        albumCard.albumPicture.setCover(CoverKind.ALBUM, album.getAlbumId());
        albumCard.albumName.setLabel(album.getAlbumName());
        albumCard.albumArtist.setLabel(album.getAlbumArtist());
        albumCard.setAlbumId(album.getAlbumId());
        albumCard.navView = albumNavView;
    }

    /** Replaces the grid's contents with the albums in the cache. */
    public void loadItems() {
        latest.run(executor, () -> {
            // The grid shows each album's artists: load them in one extra query instead of one per album.
            var albums = new QAlbumModel().albumArtists.fetchQuery().findList();
            logger.info("Loading {} albums", albums.size());
            // Built on this executor thread: GObjects of custom types created on the ForkJoin common pool (e.g. via
            // parallelStream) are broken and crash GTK as soon as they are added to a list store.
            var items = new ArrayList<AlbumListItem>(albums.size());
            for (var album : albums) {
                items.add(AlbumListItem.of(album));
            }
            return items;
        }, items -> {
            store.removeAll();
            items.forEach(store::append);
        });
    }

    @InstanceInit
    public void init() {
        this.store = new ListStore<>();
        this.sortedModel = new SortListModel<>(this.store, titleSorter);
        gridView.setModel(new NoSelection<AlbumListItem>(this.sortedModel));

        // Realized again whenever the window crosses the size breakpoint; the setup must only run once.
        AppWidgets.onFirstRealize(this, () -> {
            var win = AppWidgets.window(this);
            this.executor = AppWidgets.app(this).executors.io;

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

            var reloadAction = new SimpleAction("reload-albums", null);
            reloadAction.onActivate((_) -> {
                logger.info("Reloading albums");
                loadItems();
            });
            win.addAction(reloadAction);

            loadItems();
        });
    }

    public AlbumListView() {
    }

    public AlbumListView(MemorySegment address) {
        super(address);
    }
}
