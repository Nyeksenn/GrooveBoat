package io.github.Nyeksenn.grooveboat.ui;

import io.github.jwharm.javagi.gtk.annotations.GtkChild;
import io.github.jwharm.javagi.gtk.annotations.GtkTemplate;
import org.gnome.gtk.Box;
import org.gnome.gtk.Image;
import org.gnome.gtk.Label;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/album-card.ui", name = "AlbumCard")
public class AlbumCard extends Box {
    @GtkChild(name = "album-picture")
    public Image albumPicture;

    @GtkChild(name = "album-name")
    public Label albumName;

    @GtkChild(name = "album-artist")
    public Label albumArtist;

    public AlbumCard() {
    }
}
