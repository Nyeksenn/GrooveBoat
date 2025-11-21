package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import org.gnome.adw.NavigationPage;
import org.gnome.gio.ListStore;
import org.gnome.gio.Menu;
import org.gnome.gio.MenuItem;
import org.gnome.glib.GLib;
import org.gnome.glib.Variant;
import org.gnome.gobject.GObject;
import org.gnome.gtk.Label;
import org.gnome.gtk.ListItem;
import org.gnome.gtk.ListView;
import org.gnome.gtk.NoSelection;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

@GtkTemplate(name = "SongListPage", ui = "/io/github/Nyeksenn/grooveboat/song-list.ui")
@RegisteredType(name = "SongListPage")
public class SongListPage extends NavigationPage {
    private static final Logger logger = LoggerFactory.getLogger(SongListPage.class);
    private final String albumId;
    private ListStore<SongListItem> store;
    private Subsonic subsonic;
    private ExecutorService executor;
    private GrooveboatWindow win;

    @GtkChild(name = "album-page-label")
    public Label albumPageLabel;

    @GtkChild(name = "song-list-view")
    public ListView songListView;

    @GtkChild(name = "album-queue-menu")
    public Menu albumQueueMenu;

    @GtkCallback(name = "play-album-clicked")
    public void playAlbumClicked() {
        if (win != null) {
            var action = win.lookupAction("play-album");
            if (action != null) {
                action.activate(Variant.string(albumId));
            }
        }
    }

    @GtkCallback(name = "song-list-setup")
    public void songListSetup(GObject object) {
        var listItem = (ListItem) object;
        var songCard = new SongCard();
        listItem.setChild(songCard);
    }

    @GtkCallback(name = "song-list-bind")
    public void songListBind(GObject object) {
        var listItem = (ListItem) object;
        var songCard = (SongCard) listItem.getChild();
        var song = (SongListItem) listItem.getItem();

        songCard.songName.setLabel(song.getSongName());
        songCard.songArtist.setLabel(song.getArtistName());
        songCard.songDuration.setLabel(song.getDurationString());
        songCard.songPicture.setTexture(song.getCoverArt());
        songCard.setSongId(song.getSongId());
    }

    public void loadItems() {
        if (this.subsonic == null && this.executor == null) {
            var window = (GrooveboatWindow) getRoot();
            var albumTextureLoader = AlbumTextureLoader.getInstance();

            this.subsonic = window.subsonic;
            this.executor = window.executor;
            this.win = window;

            var menuItem = new MenuItem();
            menuItem.setLabel("Queue album");
            menuItem.setActionAndTargetValue("win.queue-album", Variant.string(albumId));
            this.albumQueueMenu.appendItem(menuItem);

            this.executor.submit(() -> {
                var songs = subsonic.getSongs(albumId);
                try {
                    var texture = albumTextureLoader.load(songs.getFirst().getAlbum().getAlbumId());
                    for (var song : songs) {
                        var songListItem = new SongListItem(
                                song.getTitle(),
                                song.getSongId(),
                                song.getDisplayArtist(),
                                song.getAlbum().getTitle(),
                                song.getDuration(),
                                texture);
                        GLib.idleAddOnce(() -> this.store.append(songListItem));
                    }
                } catch (NullPointerException e) {
                    logger.warn("Didn't find texture for an album");
                }
            });
        }
    }

    @InstanceInit
    public void init() {
        this.onRealize(this::loadItems);
        this.store = new ListStore<>();
        NoSelection<ListStore<SongListItem>> model = new NoSelection<>(this.store);
        songListView.setModel(model);
    }

    public SongListPage(String albumName, String albumId) {
        this.albumPageLabel.setLabel(albumName);
        this.albumId = albumId;
    }
}
