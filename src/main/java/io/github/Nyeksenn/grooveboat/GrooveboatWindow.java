package io.github.Nyeksenn.grooveboat;

import io.github.jwharm.javagi.gobject.annotations.InstanceInit;
import io.github.jwharm.javagi.gtk.annotations.GtkChild;
import io.github.jwharm.javagi.gtk.annotations.GtkTemplate;
import org.gnome.adw.Window;
import org.gnome.gio.MenuModel;
import org.gnome.gio.Settings;
import org.gnome.gtk.GtkBuilder;
import org.gnome.gtk.MenuButton;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/window.ui")
public class GrooveboatWindow extends Window {
    @GtkChild(name = "headerbar-menu-button")
    public MenuButton headerbarMenuButton;

    public Settings settings;

    public GrooveboatWindow(GrooveboatApp app) {
        setApplication(app);
    }

    @InstanceInit
    public void init() {
        var builder = GtkBuilder.fromResource("/io/github/Nyeksenn/grooveboat/headerbar-menu.ui");
        var menu = (MenuModel) builder.getObject("headerbar-menu");
        headerbarMenuButton.setMenuModel(menu);

        settings = new Settings("io.github.Nyeksenn.groovebox");
    }
}
