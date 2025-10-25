package io.github.Nyeksenn.grooveboat;

import io.github.Nyeksenn.grooveboat.client.SubsonicClient;
import io.github.jwharm.javagi.gobject.annotations.InstanceInit;
import io.github.jwharm.javagi.gobject.annotations.RegisteredType;
import io.github.jwharm.javagi.gtk.annotations.GtkCallback;
import io.github.jwharm.javagi.gtk.annotations.GtkChild;
import io.github.jwharm.javagi.gtk.annotations.GtkTemplate;
import org.gnome.adw.NavigationPage;
import org.gnome.gio.MenuModel;
import org.gnome.gtk.Button;
import org.gnome.gtk.GtkBuilder;
import org.gnome.gtk.MenuButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.MemorySegment;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/music-chooser.ui", name = "MusicChooser")
@RegisteredType(name = "MusicChooser")
public class MusicChooser extends NavigationPage {
    private static final Logger logger = LoggerFactory.getLogger(MusicChooser.class);

    private SubsonicClient client;

    @GtkChild(name = "headerbar-menu-button")
    public MenuButton headerbarMenuButton;

    @GtkChild(name = "sidebar-toogle-button")
    public Button sidebarToggle;

    @GtkCallback(name = "ping-button-clicked")
    public void pingButtonClicked() {
        logger.info("Button pressed");
        try {
            var ping = client.ping();
            logger.info("Server status is: {}", ping.getStatus());
        } catch (IOException e) {
            logger.warn("Couldn't connect to server");
        }
    }

    public void initClient() {
        var window = (GrooveboatWindow) getRoot();
        var app = (GrooveboatApp) window.getApplication();
        this.client = app.client;
    }

    @InstanceInit
    public void init() {
        var builder = GtkBuilder.fromResource("/io/github/Nyeksenn/grooveboat/headerbar-menu.ui");
        var menu = (MenuModel) builder.getObject("headerbar-menu");
        headerbarMenuButton.setMenuModel(menu);

        this.onRealize(this::initClient);
    }

    public MusicChooser() {
    }

    public MusicChooser(MemorySegment address) {
        super(address);
    }
}
