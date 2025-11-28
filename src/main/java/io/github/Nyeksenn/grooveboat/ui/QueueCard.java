package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.gobjects.RoundedImage;
import org.gnome.gtk.Box;
import org.gnome.gtk.Label;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/queue-item.ui", name = "QueueItem")
public class QueueCard extends Box {
    private static final Logger logger = LoggerFactory.getLogger(QueueCard.class);

    @GtkChild(name = "album-cover")
    public RoundedImage albumCover;

    @GtkChild(name = "song-title-label")
    public Label songTitleLabel;

    @GtkChild(name = "album-artist-label")
    public Label albumArtistLabel;

    public QueueCard() {
    }
}
