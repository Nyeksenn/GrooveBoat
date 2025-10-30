package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.adw.HeaderBar;
import org.gnome.adw.NavigationPage;
import org.gnome.adw.ViewSwitcher;
import org.gnome.adw.ViewSwitcherBar;
import org.gnome.gio.MenuModel;
import org.gnome.gtk.GtkBuilder;
import org.gnome.gtk.MenuButton;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gobject.annotations.RegisteredType;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/music-chooser.ui", name = "MusicChooser")
@RegisteredType(name = "MusicChooser")
public class MusicChooser extends NavigationPage {
    private static final Logger logger = LoggerFactory.getLogger(MusicChooser.class);

    @GtkChild(name = "headerbar-menu-button")
    public MenuButton headerbarMenuButton;

    @GtkChild(name = "music-chooser-header")
    public HeaderBar musicChooserHeader;

    @GtkChild(name = "music-switcher-title")
    public ViewSwitcher musicSwitcherTitle;

    @GtkChild(name = "music-switcher-bar")
    public ViewSwitcherBar musicSwitcherBar;

    @InstanceInit
    public void init() {
        var builder = GtkBuilder.fromResource("/io/github/Nyeksenn/grooveboat/headerbar-menu.ui");
        var menu = (MenuModel) builder.getObject("headerbar-menu");
        headerbarMenuButton.setMenuModel(menu);
    }

    public MusicChooser() {
    }

    public MusicChooser(MemorySegment address) {
        super(address);
    }
}
