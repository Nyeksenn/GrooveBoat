package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.gobjects.RoundedImage;
import org.gnome.adw.NavigationView;
import org.gnome.gtk.Box;
import org.gnome.gtk.Label;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/album-card.ui", name = "AlbumCard")
public class AlbumCard extends Box {
    private static final Logger logger = LoggerFactory.getLogger(AlbumCard.class);
    private String albumId;

    private GrooveboatWindow win;

    @GtkChild(name = "album-picture")
    public RoundedImage albumPicture;

    @GtkChild(name = "album-name")
    public Label albumName;

    @GtkChild(name = "album-artist")
    public Label albumArtist;

    public NavigationView navView;

    @GtkCallback(name = "album-clicked")
    public void albumClicked(int n_press, double x, double y) {
        navView.push(new SongListPage(albumName.getLabel(), albumId));
    }

    public void setAlbumId(String albumId) {
        this.albumId = albumId;
    }

    public AlbumCard() {
    }
}
