package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.gobjects.SecretKeeper;
import org.gnome.adw.ApplicationWindow;
import org.gnome.gio.Settings;
import org.gnome.gio.SimpleAction;
import org.gnome.glib.GLib;
import org.gnome.gtk.Stack;
import org.javagi.base.Out;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/window.ui", name = "GrooveboatWindow")
public class GrooveboatWindow extends ApplicationWindow {
    private static final Logger logger = LoggerFactory.getLogger(GrooveboatWindow.class);
    private final SecretKeeper secrets = SecretKeeper.getInstance();
    private final ExecutorService executor;
    private final Subsonic subsonic;

    private final Settings settings = new Settings("io.github.Nyeksenn.grooveboat");

    @GtkChild(name = "music-chooser")
    public MusicChooser musicChooser;

    @GtkChild(name = "main-stack")
    public Stack mainStack;

    private boolean checkConnection(String url, String username) {
        if (username != null && url != null) {
            var split = url.split("://");
            var password = secrets.lookupPassword(username, split[0], split[1]).join();

            subsonic.newClient(url, username, password);
            try {
                var response = subsonic.checkCredentials(username);
                if (response != null && response.status().equals("ok")) {
                    return true;
                }
            } catch (IOException e) {
                logger.warn("Connection failed: {}", e.getMessage());
            }
        }

        return false;
    }

    public void initializeClient() {
        mainStack.setVisibleChildName("loading-page");

        final String username = settings.getString("username");
        final String url = settings.getString("url");

        if (username.isBlank() || url.isBlank()) {
            var setupDialog = new SetupDialog();
            var width = new Out<Integer>();
            var height = new Out<Integer>();
            this.getDefaultSize(width, height);
            setupDialog.setContentHeight(height.get());
            setupDialog.setContentWidth(width.get());
            setupDialog.present(this);
        } else {
            executor.submit(() -> {
                var connectionSuccessful = checkConnection(url, username);
                if (connectionSuccessful) {
                    subsonic.refreshRemoteData();
                    GLib.idleAddOnce(() -> mainStack.setVisibleChildName("content-page"));
                } else {
                    GLib.idleAddOnce(() -> mainStack.setVisibleChildName("error-page"));
                }
            });
        }
    }

    @InstanceInit
    public void init() {
        onRealize(() -> {
            var app = (GrooveboatApp) getApplication();
            this.initializeClient();
            var refreshAction = new SimpleAction("refresh-data", null);
            refreshAction.onActivate((_) -> {
                executor.submit(subsonic::refreshRemoteData);
            });
            app.addAction(refreshAction);
            app.queueStore.loadState();
        });
    }

    public GrooveboatWindow(GrooveboatApp app) {
        setApplication(app);
        this.executor = app.executor;
        this.subsonic = app.subsonic;
    }

    @GtkCallback(name = "breakpoint-apply")
    public void handleBreakpointApply() {
        musicChooser.miniPlayer.setVisible(true);
        musicChooser.musicSwitcherBar.setReveal(true);
        musicChooser.musicChooserHeader.setTitleWidget(null);
    }

    @GtkCallback(name = "breakpoint-unapply")
    public void handleBreakpointUnapply() {
        musicChooser.miniPlayer.setVisible(false);
        musicChooser.musicSwitcherBar.setReveal(false);
        musicChooser.musicChooserHeader.setTitleWidget(musicChooser.musicSwitcherTitle);
    }
}
