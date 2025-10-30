package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.adw.NavigationPage;
import org.gnome.gtk.Label;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.Property;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

@GtkTemplate(name = "SongListPage", ui = "/io/github/Nyeksenn/grooveboat/song-list.ui")
@RegisteredType(name = "SongListPage")
public class SongListPage extends NavigationPage {
    private String albumName;

    @Property(name = "album-name")
    public String getAlbumName() {
        return albumName;
    }

    public void setAlbumName(String albumName) {
        this.albumName = albumName;
    }

    @GtkChild(name = "album-page-label")
    public Label albumPageLabel;

    @InstanceInit
    public void init() {
    }

    public SongListPage(String albumName) {
        this.albumPageLabel.setLabel(albumName);
    }
}
