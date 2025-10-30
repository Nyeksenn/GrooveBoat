package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.adw.NavigationPage;
import org.gnome.gtk.Label;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

@GtkTemplate(name = "SongListPage", ui = "/io/github/Nyeksenn/grooveboat/song-list.ui")
@RegisteredType(name = "SongListPage")
public class SongListPage extends NavigationPage {
    private final String albumName;

    @GtkChild(name = "album-page-label")
    public Label albumPageLabel;

    public void realize() {
        this.albumPageLabel.setText(albumName);
    }

    @InstanceInit
    public void init() {
        this.onRealize(this::realize);
    }

    public SongListPage(String albumName) {
        this.albumName = albumName;
    }
}
