package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.adw.Avatar;
import org.gnome.adw.NavigationView;
import org.gnome.glib.GLib;
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

    /**
     * Shows the artist's image at the size level that covers the avatar's physical pixels. The card is recycled by
     * the grid, so a result is dropped when the card was rebound to another artist meanwhile.
     */
    public void loadImage() {
        var id = artistId;
        var native_ = getNative();
        var surface = native_ == null ? null : native_.getSurface();
        var scale = surface == null ? getScaleFactor() : surface.getScale();
        var bucket = CoverSizes.bucketFor(artistPicture.getSize(), scale);
        var loader = CoverLoader.getInstance();
        artistPicture.setCustomImage(loader.peekBest(CoverKind.ARTIST, id, bucket));
        if (loader.peekExact(CoverKind.ARTIST, id, bucket) != null) {
            return;
        }
        loader.loadAsync(CoverKind.ARTIST, id, bucket).thenAccept(result -> GLib.idleAddOnce(() -> {
            if (id.equals(artistId) && result.isPresent()) {
                artistPicture.setCustomImage(result.get());
            }
        }));
    }

    public ArtistCard() {
    }
}
