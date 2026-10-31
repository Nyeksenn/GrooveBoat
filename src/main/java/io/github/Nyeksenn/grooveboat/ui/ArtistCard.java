package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.adw.Avatar;
import org.gnome.adw.NavigationView;
import org.gnome.gtk.Box;
import org.gnome.gtk.Label;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/artist-card.ui", name = "ArtistCard")
public class ArtistCard extends Box {
    private String artistId;

    @GtkChild(name = "artist-picture")
    public Avatar artistPicture;

    @GtkChild(name = "artist-name")
    public Label artistName;

    public NavigationView navView;

    @GtkCallback(name = "artist-clicked")
    public void artistClicked(int n_press, double x, double y) {
        navView.push(new ArtistSongsPage(artistName.getLabel(), artistId));
    }

    public String getArtistId() {
        return artistId;
    }

    public void setArtistId(String artistId) {
        this.artistId = artistId;
    }

    public ArtistCard() {
    }
}
