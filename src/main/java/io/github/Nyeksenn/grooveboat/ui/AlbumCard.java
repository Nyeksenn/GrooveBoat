package io.github.Nyeksenn.grooveboat.ui;

import io.github.jwharm.javagi.gtk.annotations.GtkChild;
import io.github.jwharm.javagi.gtk.annotations.GtkTemplate;
import org.gnome.gtk.Box;
import org.gnome.gtk.Label;
import org.gnome.gtk.Picture;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/album-card.ui", name = "AlbumCard")
public class AlbumCard extends Box {
    @GtkChild(name = "album-picture")
    public Picture albumPicture;

    @GtkChild(name = "album-name")
    public Label albumName;

    public AlbumCard() {}
}
