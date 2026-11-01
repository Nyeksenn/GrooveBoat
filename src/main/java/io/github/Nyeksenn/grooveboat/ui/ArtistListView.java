package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.entities.query.QArtistModel;
import io.github.Nyeksenn.grooveboat.gobjects.ArtistListItem;
import org.gnome.adw.NavigationView;
import org.gnome.gio.ListStore;
import org.gnome.gio.SimpleAction;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;

/** Grid of the library's album artists, sorted by name. */
@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/artist-list.ui", name = "ArtistListView")
@RegisteredType(name = "ArtistListView")
public class ArtistListView extends Box {
    private static final Logger logger = LoggerFactory.getLogger(ArtistListView.class);
    private final ListStore<ArtistListItem> store = new ListStore<>();
    private ExecutorService executor;
    /** Only the newest load is applied, so overlapping reloads can't add artists twice. */
    private final UiAsync.Latest latest = new UiAsync.Latest();

    @GtkChild(name = "artist-nav-view")
    public NavigationView artistNavView;

    @GtkChild(name = "artist-grid-view")
    public GridView artistGridView;

    @GtkCallback(name = "artist-grid-setup")
    public void artistGridSetup(GObject object) {
        var listItem = (ListItem) object;
        listItem.setChild(new ArtistCard());
    }

    @GtkCallback(name = "artist-grid-bind")
    public void artistGridBind(GObject object) {
        var listItem = (ListItem) object;
        var artistCard = (ArtistCard) listItem.getChild();
        var artist = (ArtistListItem) listItem.getItem();

        artistCard.artistName.setLabel(artist.getArtistName());
        artistCard.artistPicture.setText(artist.getArtistName());
        artistCard.artistPicture.setCustomImage(artist.getCoverArt());
        artistCard.setArtistId(artist.getArtistId());
        artistCard.navView = artistNavView;
    }

    /** Replaces the grid's contents with the album artists in the cache. */
    public void loadItems() {
        var coverLoader = CoverLoader.getInstance();
        latest.run(executor, () -> {
            var artists = new QArtistModel().albums.isNotEmpty().findList();
            artists.sort(Comparator.comparing(a -> a.getName() == null ? "" : a.getName(),
                    String.CASE_INSENSITIVE_ORDER));
            logger.info("Loading {} artists", artists.size());
            // Built on this executor thread, not the ForkJoin common pool (see AlbumListView).
            var items = new ArrayList<ArtistListItem>(artists.size());
            for (var artist : artists) {
                items.add(new ArtistListItem(coverLoader.loadArtist(artist.getId()), artist.getName(),
                        artist.getId()));
            }
            return items;
        }, items -> {
            store.removeAll();
            items.forEach(store::append);
        });
    }

    @InstanceInit
    public void init() {
        artistGridView.setModel(new NoSelection<>(store));
        // Realized again whenever the window crosses the size breakpoint; the setup must only run once.
        AppWidgets.onFirstRealize(this, () -> {
            var win = AppWidgets.window(this);
            this.executor = AppWidgets.app(this).executors.io;

            var reloadAction = new SimpleAction("reload-artists", null);
            reloadAction.onActivate((_) -> {
                logger.info("Reloading artists");
                CoverLoader.getInstance().clearArtists();
                loadItems();
            });
            win.addAction(reloadAction);

            loadItems();
        });
    }

    public ArtistListView() {
    }

    public ArtistListView(MemorySegment address) {
        super(address);
    }
}
