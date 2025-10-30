package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.gtk.Box;
import org.gnome.gtk.Image;
import org.gnome.gtk.Label;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

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
