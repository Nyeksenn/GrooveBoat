package io.github.Nyeksenn.grooveboat.ui;

import static org.javagi.util.Intl.i18n;
import io.github.Nyeksenn.grooveboat.gobjects.RoundedImage;
import io.github.Nyeksenn.grooveboat.gobjects.SongListItem;
import org.gnome.gio.Menu;
import org.gnome.gio.MenuItem;
import org.gnome.glib.Variant;
import org.gnome.gtk.Box;
import org.gnome.gtk.Label;
import org.gnome.gtk.ListBox;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

import java.util.List;

/** One album on the artist page: a header with cover, title, year and play/queue, above a boxed list of songs. */
@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/artist-album-section.ui", name = "ArtistAlbumSection")
public class ArtistAlbumSection extends Box {
    private String albumId;

    @GtkChild(name = "album-cover")
    public RoundedImage albumCover;

    @GtkChild(name = "album-title")
    public Label albumTitle;

    @GtkChild(name = "album-year")
    public Label albumYear;

    @GtkChild(name = "song-list")
    public ListBox songList;

    @GtkChild(name = "album-queue-menu")
    public Menu albumQueueMenu;

    @GtkCallback(name = "play-album-clicked")
    public void playAlbumClicked() {
        activateActionVariant("app.play-album", Variant.string(albumId));
    }

    /** Must be called on the main thread; the song items may be built elsewhere. */
    public ArtistAlbumSection(String albumId, String title, int year, List<SongListItem> songs) {
        this.albumId = albumId;
        albumCover.setCover(CoverKind.ALBUM, albumId);
        albumTitle.setLabel(title);
        if (year > 0) {
            albumYear.setLabel(String.valueOf(year));
        } else {
            albumYear.setVisible(false);
        }

        var queueItem = new MenuItem();
        queueItem.setLabel(i18n("Queue album"));
        queueItem.setActionAndTargetValue("app.queue-album", Variant.string(albumId));
        albumQueueMenu.appendItem(queueItem);

        for (var song : songs) {
            var card = new SongCard();
            card.songName.setLabel(song.getSongName());
            card.songArtist.setLabel(song.getArtistName());
            card.songDuration.setLabel(song.getDurationString());
            // The section header already shows the album cover.
            card.songPicture.setVisible(false);
            card.setTrackNumber(song.getTrackNumber());
            card.setSongId(song.getSongId());
            songList.append(card);
        }
    }

    public ArtistAlbumSection() {
    }
}
