package io.github.Nyeksenn.grooveboat.ui;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/song-card.ui", name = "SongCard")
public class SongCard extends Box {
    private static final Logger logger = LoggerFactory.getLogger(SongCard.class);
    private String songId;

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
        activateActionIfExists("win.play-song", Variant.string(songId));
    }

    @GtkCallback(name = "song-right-clicked")
    public void songRightClicked(int n_press, double x, double y) {
        var pos = new Rectangle((int) x, (int) y, 0, 0);
        songPopover.setPointingTo(pos);
        songPopover.popup();
    }

    public void setSongId(String songId) {
        var item = new MenuItem();
        item.setLabel("Add to queue");
        item.setActionAndTargetValue("win.queue-song", Variant.string(songId));
        songContextMenu.appendItem(item);
        this.songId = songId;
    }

    public SongCard() {
    }
}
