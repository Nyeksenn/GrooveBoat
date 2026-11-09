package io.github.Nyeksenn.grooveboat.ui;

import static org.javagi.util.Intl.i18n;
import io.github.Nyeksenn.grooveboat.gobjects.RoundedImage;
import org.gnome.gdk.Rectangle;
import org.gnome.gio.Menu;
import org.gnome.gio.MenuItem;
import org.gnome.glib.Variant;
import org.gnome.gtk.Box;
import org.gnome.gtk.Label;
import org.gnome.gtk.PopoverMenu;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/song-card.ui", name = "SongCard")
public class SongCard extends Box {
    private String songId;

    @GtkChild(name = "song-track")
    public Label songTrack;

    @GtkChild(name = "song-picture")
    public RoundedImage songPicture;

    @GtkChild(name = "song-name")
    public Label songName;

    @GtkChild(name = "song-artist")
    public Label songArtist;

    @GtkChild(name = "song-duration")
    public Label songDuration;

    @GtkChild(name = "song-context-menu")
    public Menu songContextMenu;

    @GtkChild(name = "song-popover")
    public PopoverMenu songPopover;

    @GtkCallback(name = "song-clicked")
    public void songClicked(int n_press, double x, double y) {
        activateActionVariant("app.play-song", Variant.string(songId));
    }

    @GtkCallback(name = "song-right-clicked")
    public void songRightClicked(int n_press, double x, double y) {
        var pos = new Rectangle((int) x, (int) y, 0, 0);
        songPopover.setPointingTo(pos);
        songPopover.popup();
    }

    /** Shows the track number; a card is recycled, so a missing number (0) hides it again. */
    public void setTrackNumber(int track) {
        songTrack.setVisible(track > 0);
        songTrack.setLabel(track > 0 ? String.valueOf(track) : "");
    }

    public void setSongId(String songId) {
        // Cards are recycled by list views: replace the entry instead of adding another one per bind.
        songContextMenu.removeAll();
        var item = new MenuItem();
        item.setLabel(i18n("Add to queue"));
        item.setActionAndTargetValue("app.queue-song", Variant.string(songId));
        songContextMenu.appendItem(item);
        this.songId = songId;
    }

    public SongCard() {
    }
}
