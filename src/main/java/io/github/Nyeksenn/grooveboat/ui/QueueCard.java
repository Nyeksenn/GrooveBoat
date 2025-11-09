package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.gobjects.RoundedImage;
import org.gnome.gtk.Box;
import org.gnome.gtk.Label;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/queue-card.ui")
@RegisteredType(name = "QueueCard")
public class QueueCard extends Box {
    private static final Logger logger = LoggerFactory.getLogger(QueueCard.class);

    private String songId;

    @GtkChild(name = "song-picture")
    public RoundedImage songPicture;

    @GtkChild(name = "song-name")
    public Label songName;

    @GtkChild(name = "song-artist")
    public Label songArtist;

    @GtkChild(name = "song-duration")
    public Label songDuration;

    public void setSongId(String songId) {
        this.songId = songId;
    }

    public QueueCard(MemorySegment address) {
        super(address);
    }

    public QueueCard() {
    }
}
