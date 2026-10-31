package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.gobjects.RoundedImage;
import org.gnome.glib.Variant;
import org.gnome.gtk.Box;
import org.gnome.gtk.Label;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/queue-item.ui", name = "QueueItem")
public class QueueCard extends Box {
    private static final Logger logger = LoggerFactory.getLogger(QueueCard.class);
    private int index;

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

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
