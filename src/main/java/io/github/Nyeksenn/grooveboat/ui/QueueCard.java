package io.github.Nyeksenn.grooveboat.ui;

import static org.javagi.util.Intl.i18n;

import io.github.Nyeksenn.grooveboat.gobjects.RoundedImage;
import org.gnome.glib.Variant;
import org.gnome.gtk.AccessibleProperty;
import org.gnome.gtk.Box;
import org.gnome.gtk.Image;
import org.gnome.gtk.Label;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/queue-item.ui", name = "QueueItem")
public class QueueCard extends Box {
    private int index;

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    private boolean playing;

    public boolean getPlaying() {
        return playing;
    }

    /** Marks this card as the current song: icon, accent styling and a screen-reader description. */
    public void setPlaying(boolean playing) {
        this.playing = playing;
        playingIcon.setVisible(playing);
        if (playing) {
            addCssClass("now-playing");
            songTitleLabel.addCssClass("heading");
            updateProperty(AccessibleProperty.DESCRIPTION, i18n("Now playing"));
        } else {
            removeCssClass("now-playing");
            songTitleLabel.removeCssClass("heading");
            resetProperty(AccessibleProperty.DESCRIPTION);
        }
    }

    @GtkChild(name = "playing-icon")
    public Image playingIcon;

    @GtkChild(name = "album-cover")
    public RoundedImage albumCover;

    @GtkChild(name = "song-title-label")
    public Label songTitleLabel;

    @GtkChild(name = "album-artist-label")
    public Label albumArtistLabel;

    @GtkCallback(name = "remove-from-queue")
    public void removeFromQueue() {
        activateActionVariant("app.remove-song", Variant.int32(index));
    }

    public QueueCard() {
    }
}
