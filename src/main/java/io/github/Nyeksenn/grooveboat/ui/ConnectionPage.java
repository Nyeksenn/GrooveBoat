package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.client.Subsonic;
import org.apache.commons.validator.routines.UrlValidator;
import org.gnome.adw.EntryRow;
import org.gnome.adw.NavigationPage;
import org.gnome.adw.Toast;
import org.gnome.adw.ToastOverlay;
import org.gnome.gio.Settings;
import org.gnome.glib.GLib;
import org.gnome.glib.Variant;
import org.gnome.gtk.Button;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.semver4j.Semver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.foreign.MemorySegment;
import java.util.concurrent.ExecutorService;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/connection-page.ui", name = "ConnectionPage")
public class ConnectionPage extends NavigationPage {
    private final Settings settings = new Settings("io.github.Nyeksenn.grooveboat");
    private static final Logger logger = LoggerFactory.getLogger(ConnectionPage.class);
    private final UrlValidator validator = new UrlValidator();
    private final Semver minVersion = new Semver("1.16.1");
    private ExecutorService exec;
    private Subsonic subsonic;

    @GtkChild(name = "connection-next-button")
    public Button connectionNextButton;

    @GtkChild(name = "connection-overlay")
    public ToastOverlay connectionOverlay;

    @GtkChild(name = "entry-url")
    public EntryRow entryUrl;

    @GtkCallback(name = "host-url-changed")
    public void hostUrlChanged() {
        connectionNextButton.setVisible(true);
    }

    @GtkCallback(name = "url-validation")
    public void urlValidation() {
        assert exec != null;
        assert subsonic != null;

        var url = entryUrl.getText();

        var future = exec.submit(() -> {
            if (validator.isValid(url)) {
                subsonic.initClient(url, "jdoe", "");
                try {
                    var resp = subsonic.ping();
                    if (resp.status().equals("failed")) {
                        var version = new Semver(resp.version());
                        if (resp.openSubsonic() && (version.isGreaterThanOrEqualTo(minVersion))) {
                            settings.setString("url", url);
                            GLib.idleAddOnce(() -> activateActionIfExists("navigation.push", Variant.string("credentials-page")));
                        } else {
                            GLib.idleAddOnce(() -> {
                                connectionOverlay.addToast(new Toast("Incompatible Server"));
                                entryUrl.setCssClasses(new String[]{"error"});
                            });
                        }
                    } else {
                        throw new IOException("Server Error");
                    }
                } catch (IOException e) {
                    logger.warn("Couldn't reach server: {}", e.getMessage());
                    GLib.idleAddOnce(() -> {
                        connectionOverlay.addToast(new Toast("Unable to reach server"));
                        entryUrl.setCssClasses(new String[]{"error"});
                    });
                }
            } else {
                GLib.idleAddOnce(() -> {
                    connectionOverlay.addToast(new Toast("URL invalid"));
                    entryUrl.setCssClasses(new String[]{"error"});
                });
            }
        });
    }

    @InstanceInit
    public void init() {
        onRealize(() -> {
            var win = (GrooveboatWindow) getRoot();
            this.exec = win.executor;
            this.subsonic = win.subsonic;
        });
    }

    public ConnectionPage(MemorySegment address) {
        super(address);
    }
}
