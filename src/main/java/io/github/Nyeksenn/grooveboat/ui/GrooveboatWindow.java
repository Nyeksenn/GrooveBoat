package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.client.SubsonicClient;
import io.github.Nyeksenn.grooveboat.gobjects.SecretKeeper;
import org.gnome.adw.ApplicationWindow;
import org.gnome.gio.Settings;
import org.gnome.glib.GLib;
import org.gnome.gtk.Stack;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/window.ui", name = "GrooveboatWindow")
public class GrooveboatWindow extends ApplicationWindow {
    private static final Logger logger = LoggerFactory.getLogger(GrooveboatWindow.class);
    private final SecretKeeper secrets = SecretKeeper.getInstance();
    public final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    private final Settings settings = new Settings("io.github.Nyeksenn.groovebox");

    @GtkChild(name = "music-chooser")
    public MusicChooser musicChooser;

    @GtkChild(name = "main-stack")
    public Stack mainStack;

    public Subsonic subsonic;

    public void realizeHandler() {
        mainStack.setVisibleChildName("loading-page");
        executor.submit(() -> {
            String username, url, password = null;
            var connectionSuccessful = false;

            // Setup Client on Startup
            username = settings.getString("username");
            url = settings.getString("url");

            if (username != null && url != null) {
                var split = url.split("://");
                password = secrets.lookupPassword(username, split[0], split[1]).join();
            }

            if (password != null) {
                var client = new SubsonicClient(url, username, password);
                this.subsonic = new Subsonic(client);

                try {
                    var response = subsonic.checkCredentials(username);
                    if (response != null && response.status().equals("ok")) {
                        connectionSuccessful = true;
                    }
                    GLib.idleAddOnce(() -> mainStack.setVisibleChildName("content-page"));
                } catch (IOException e) {
                    logger.warn("Connection failed: {}", e.getMessage());
                }
            }

            if (!connectionSuccessful) {
                GLib.idleAddOnce(() -> mainStack.setVisibleChildName("error-page"));
            }
        });
    }

    public void unrealizeHandler() {
        try {
            subsonic.close();
        } catch (Exception e) {
            logger.warn("Unable to cleanup client");
        }
        executor.shutdown();
    }

    @InstanceInit
    public void init() {
        onRealize(this::realizeHandler);
        onUnrealize(this::unrealizeHandler);
    }


    public GrooveboatWindow(GrooveboatApp app) {
        setApplication(app);
    }

    @GtkCallback(name = "breakpoint-apply")
    public void handleBreakpointApply() {
//        musicChooser.sidebarToggle.setVisible(true);
        musicChooser.musicSwitcherBar.setReveal(true);
        musicChooser.musicChooserHeader.setTitleWidget(null);
    }

    @GtkCallback(name = "breakpoint-unapply")
    public void handleBreakpointUnapply() {
//        musicChooser.sidebarToggle.setVisible(false);
        musicChooser.musicSwitcherBar.setReveal(false);
        musicChooser.musicChooserHeader.setTitleWidget(musicChooser.musicSwitcherTitle);
    }
}
