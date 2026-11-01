package io.github.Nyeksenn.grooveboat.ui;

import static org.javagi.util.Intl.i18n;
import io.github.Nyeksenn.grooveboat.backend.BackendFactory;
import io.github.Nyeksenn.grooveboat.backend.BackendSupport;
import io.github.Nyeksenn.grooveboat.backend.BackendType;
import io.github.Nyeksenn.grooveboat.backend.MusicBackend;
import io.github.Nyeksenn.grooveboat.backend.ServerInfo;
import org.gnome.adw.EntryRow;
import org.gnome.adw.NavigationPage;
import org.gnome.adw.PreferencesGroup;
import org.gnome.adw.Toast;
import org.gnome.adw.ToastOverlay;
import org.gnome.glib.GLib;
import org.gnome.glib.Variant;
import org.gnome.gtk.Button;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.ExecutorService;

/** Setup step that checks the server URL with the chosen backend's probe. */
@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/connection-page.ui", name = "ConnectionPage")
public class ConnectionPage extends NavigationPage {
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPage.class);
    private ExecutorService exec;

    @GtkChild(name = "connection-next-button")
    public Button connectionNextButton;

    @GtkChild(name = "connection-overlay")
    public ToastOverlay connectionOverlay;

    @GtkChild(name = "connection-group")
    public PreferencesGroup connectionGroup;

    @GtkChild(name = "entry-url")
    public EntryRow entryUrl;

    @GtkCallback(name = "host-url-changed")
    public void hostUrlChanged() {
        connectionNextButton.setVisible(true);
        entryUrl.removeCssClass("error");
    }

    private void fail(String message) {
        GLib.idleAddOnce(() -> {
            connectionNextButton.setSensitive(true);
            var toast = new Toast(message);
            toast.setUseMarkup(false);
            connectionOverlay.addToast(toast);
            entryUrl.addCssClass("error");
        });
    }

    private static ServerInfo probeQuietly(BackendType type, String url) {
        try (var backend = BackendFactory.create(type, url)) {
            return backend.probe();
        } catch (IOException | RuntimeException e) {
            return ServerInfo.incompatible(e.getMessage());
        }
    }

    @GtkCallback(name = "url-validation")
    public void urlValidation() {
        var dialog = SetupDialog.of(this);
        var type = dialog.getBackendType();
        var url = BackendSupport.normalizeBaseUrl(entryUrl.getText());

        if (!BackendSupport.isValidBaseUrl(url)) {
            fail("URL invalid");
            return;
        }

        connectionNextButton.setSensitive(false);
        exec.submit(() -> {
            MusicBackend backend = null;
            ServerInfo info;
            try {
                backend = BackendFactory.create(type, url);
                info = backend.probe();
            } catch (IOException | RuntimeException e) {
                // RuntimeExceptions (bad URL, unexpected response) must not leave the button disabled.
                logger.warn("Couldn't reach server: {}", e.getMessage());
                info = null;
            }

            if (info instanceof ServerInfo.Compatible(var name, var version)) {
                final var found = backend;
                logger.info("Found {} {} at {}", name, version, found.host());
                GLib.idleAddOnce(() -> {
                    connectionNextButton.setSensitive(true);
                    dialog.setCandidate(found);
                    activateActionVariant("navigation.push", Variant.string("credentials-page"));
                });
                return;
            }
            if (backend != null) {
                backend.close();
            }

            // Wrong server type picked? Point the user at the right one.
            var other = type.other();
            if (probeQuietly(other, url) instanceof ServerInfo.Compatible) {
                fail(String.format(i18n("This looks like a %1$s server. Go back and choose %1$s."), other.displayName()));
            } else if (info == null) {
                fail(i18n("Unable to reach server"));
            } else {
                var reason = info instanceof ServerInfo.Incompatible(var r) ? r : null;
                fail(reason != null ? reason : i18n("Incompatible server"));
            }
        });
    }

    @InstanceInit
    public void init() {
        onMap(() -> {
            var type = SetupDialog.of(this).getBackendType();
            if (type != null) {
                connectionGroup.setTitle(String.format(i18n("%s Server"), type.displayName()));
                connectionGroup.setDescription(String.format(
                        i18n("Enter the address of your %s server, for example https://music.example.com"),
                        type.displayName()));
            }
            entryUrl.grabFocus();
        });
        onRealize(() -> this.exec = AppWidgets.app(this).executor);
    }

    public ConnectionPage(MemorySegment address) {
        super(address);
    }
}
