package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.backend.BackendFactory;
import io.github.Nyeksenn.grooveboat.backend.BackendSupport;
import io.github.Nyeksenn.grooveboat.backend.BackendType;
import io.github.Nyeksenn.grooveboat.backend.ServerInfo;
import org.apache.commons.validator.routines.UrlValidator;
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
    private final UrlValidator validator = new UrlValidator(new String[]{"http", "https"},
            UrlValidator.ALLOW_LOCAL_URLS);
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
            connectionOverlay.addToast(new Toast(message));
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

        if (!validator.isValid(url)) {
            fail("URL invalid");
            return;
        }

        connectionNextButton.setSensitive(false);
        exec.submit(() -> {
            var backend = BackendFactory.create(type, url);
            ServerInfo info;
            try {
                info = backend.probe();
            } catch (IOException e) {
                logger.warn("Couldn't reach server: {}", e.getMessage());
                info = null;
            }

            if (info != null && info.compatible()) {
                logger.info("Found {} {} at {}", info.name(), info.version(), backend.host());
                GLib.idleAddOnce(() -> {
                    connectionNextButton.setSensitive(true);
                    dialog.setCandidate(backend);
                    activateActionVariant("navigation.push", Variant.string("credentials-page"));
                });
                return;
            }
            backend.close();

            // Wrong server type picked? Point the user at the right one.
            var other = type.other();
            if (probeQuietly(other, url).compatible()) {
                fail("This looks like a " + other.displayName() + " server. Go back and choose "
                        + other.displayName() + ".");
            } else if (info == null) {
                fail("Unable to reach server");
            } else {
                fail(info.reason() != null ? info.reason() : "Incompatible server");
            }
        });
    }

    @InstanceInit
    public void init() {
        onMap(() -> {
            var type = SetupDialog.of(this).getBackendType();
            if (type != null) {
                connectionGroup.setTitle(type.displayName() + " Server");
                connectionGroup.setDescription("Enter the address of your " + type.displayName()
                        + " server, for example https://music.example.com");
            }
            entryUrl.grabFocus();
        });
        onRealize(() -> {
            var win = (GrooveboatWindow) getRoot();
            var app = (GrooveboatApp) win.getApplication();
            this.exec = app.executor;
        });
    }

    public ConnectionPage(MemorySegment address) {
        super(address);
    }
}
