package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.gtk.Box;
import org.gnome.gtk.Image;
import org.gnome.gtk.Label;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/song-card.ui")
@RegisteredType(name = "SongCard")
public class SongCard extends Box {
    private static final Logger logger = LoggerFactory.getLogger(GrooveboatApp.class);

    @GtkChild(name = "song-picture")
    public Image songPicture;

    @GtkChild(name = "song-name")
    public Label songName;

    @GtkChild(name = "song-artist")
    public Label songArtist;

    @GtkChild(name = "song-duration")
    public Label songDuration;

    @GtkCallback(name = "song-clicked")
    public void songClicked(int n_press, double x, double y) {
        logger.info("Clicked on song {}", songName.getLabel());
    }

    public SongCard(MemorySegment address) {
        super(address);
    }

    public SongCard() {
    }
}
