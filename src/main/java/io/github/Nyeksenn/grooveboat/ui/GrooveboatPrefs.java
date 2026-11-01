package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.backend.MusicBackend;
import io.github.Nyeksenn.grooveboat.backend.ServerInfo;
import io.github.Nyeksenn.grooveboat.utils.AppSettings;
import org.gnome.adw.ActionRow;
import org.gnome.adw.PreferencesDialog;
import org.gnome.glib.GLib;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

/**
 * Shows the current session's server and account read-only. The connection can't be edited here: a session's
 * backend and server are fixed, and changing them means logging out.
 */
@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/prefs.ui", name = "GrooveboatPrefs")
public class GrooveboatPrefs extends PreferencesDialog {
    private static final Logger logger = LoggerFactory.getLogger(GrooveboatPrefs.class);

    @GtkChild(name = "row-backend")
    public ActionRow rowBackend;

    @GtkChild(name = "row-url")
    public ActionRow rowUrl;

    @GtkChild(name = "row-version")
    public ActionRow rowVersion;

    @GtkChild(name = "row-username")
    public ActionRow rowUsername;

    @GtkCallback(name = "logout-clicked")
    public void logoutClicked() {
        // Activate while still attached to the window, so the action resolves; then get out of the way.
        activateActionVariant("app.logout", null);
        close();
    }

    public GrooveboatPrefs(MusicBackend backend, ExecutorService exec) {
        rowBackend.setSubtitle(backend.type().displayName());
        rowUrl.setSubtitle(GLib.markupEscapeText(backend.baseUrl(), -1));
        rowUsername.setSubtitle(GLib.markupEscapeText(AppSettings.get().getString(AppSettings.USERNAME), -1));

        UiAsync.background(exec, () -> {
            String version;
            try {
                version = switch (backend.probe()) {
                    case ServerInfo.Compatible(var name, var v) -> name + " " + v;
                    case ServerInfo.Incompatible _ -> "Unknown";
                };
            } catch (IOException e) {
                logger.warn("Couldn't query server version: {}", e.getMessage());
                version = "Unreachable";
            }
            final var text = GLib.markupEscapeText(version, -1);
            GLib.idleAddOnce(() -> rowVersion.setSubtitle(text));
        });
    }
}
