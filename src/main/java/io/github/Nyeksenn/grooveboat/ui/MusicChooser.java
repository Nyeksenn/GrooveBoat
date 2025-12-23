package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.adw.HeaderBar;
import org.gnome.adw.NavigationPage;
import org.gnome.adw.ViewSwitcher;
import org.gnome.adw.ViewSwitcherBar;
import org.gnome.gtk.Button;
import org.gnome.gtk.SearchBar;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/music-chooser.ui", name = "MusicChooser")
@RegisteredType(name = "MusicChooser")
public class MusicChooser extends NavigationPage {
    private static final Logger logger = LoggerFactory.getLogger(MusicChooser.class);

    @GtkChild(name = "music-chooser-header")
    public HeaderBar musicChooserHeader;

    @GtkChild(name = "music-switcher-title")
    public ViewSwitcher musicSwitcherTitle;

    @GtkChild(name = "music-switcher-bar")
    public ViewSwitcherBar musicSwitcherBar;

    @GtkChild(name = "mini-player")
    public MiniPlayer miniPlayer;

    @GtkChild(name = "album-searchbar")
    public SearchBar albumSearchbar;

    @GtkChild(name = "button-search")
    public Button buttonSearch;

    @GtkCallback(name = "search-clicked")
    public void searchClicked() {
        albumSearchbar.setSearchMode(!albumSearchbar.getSearchMode());
    }

    @InstanceInit
    public void init() {
    }

    public MusicChooser() {
    }

    public MusicChooser(MemorySegment address) {
        super(address);
    }
}
