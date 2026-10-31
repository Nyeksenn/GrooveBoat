package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.entities.query.QAlbumModel;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import org.gnome.adw.NavigationPage;
import org.gnome.glib.GLib;
import org.gnome.gtk.Box;
import org.gnome.gtk.Label;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

/** Pushed when an artist card is clicked: the artist's songs, grouped by album, newest album first. */
@GtkTemplate(name = "ArtistSongsPage", ui = "/io/github/Nyeksenn/grooveboat/artist-songs.ui")
@RegisteredType(name = "ArtistSongsPage")
public class ArtistSongsPage extends NavigationPage {
    private static final Logger logger = LoggerFactory.getLogger(ArtistSongsPage.class);
    private final String artistId;
    /** Realized again whenever the window crosses the size breakpoint; load only once. */
    private boolean initialized = false;

    @GtkChild(name = "artist-page-label")
    public Label artistPageLabel;

    @GtkChild(name = "album-sections")
    public Box albumSections;

    @GtkChild(name = "empty-label")
    public Label emptyLabel;

    private void loadItems() {
        if (initialized) {
            return;
        }
        var win = (GrooveboatWindow) getRoot();
        var app = (GrooveboatApp) win.getApplication();
        var library = app.getLibrary();
        if (library == null) {
            return;
        }
        initialized = true;
        var coverLoader = CoverLoader.getInstance();

        app.executor.submit(() -> {
            var albums = new QAlbumModel().albumArtists.id.equalTo(artistId)
                    .orderBy().release_year.desc().title.asc().findList();
            logger.info("Loading {} albums for artist {}", albums.size(), artistId);
            var anySongs = false;
            for (var album : albums) {
                var songs = library.getSongs(album.getId());
                if (songs.isEmpty()) {
                    continue;
                }
                anySongs = true;
                var cover = coverLoader.load(album.getId());
                // Built on this executor thread, not the ForkJoin common pool (see AlbumListView).
                var items = new ArrayList<SongListItem>(songs.size());
                for (var song : songs) {
                    items.add(new SongListItem(song.getTitle(), song.getId(), song.getDisplayArtist(),
                            album.getTitle(), song.getDuration(), song.getContentType(), cover));
                }
                var albumId = album.getId();
                var title = album.getTitle();
                var year = album.getRelease_year();
                // One section per album as soon as it is ready; widgets are created on the main thread.
                GLib.idleAddOnce(() -> albumSections.append(
                        new ArtistAlbumSection(albumId, title, year, cover, items)));
            }
            if (!anySongs) {
                GLib.idleAddOnce(() -> emptyLabel.setVisible(true));
            }
        });
    }

    @InstanceInit
    public void init() {
        onRealize(this::loadItems);
    }

    public ArtistSongsPage(String artistName, String artistId) {
        this.artistId = artistId;
        setTitle(artistName);
        artistPageLabel.setLabel(artistName);
    }
}
