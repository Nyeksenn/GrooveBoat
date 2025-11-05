package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.client.Subsonic;
import io.github.Nyeksenn.grooveboat.gobjects.SecretKeeper;
import org.apache.commons.validator.routines.UrlValidator;
import org.gnome.adw.EntryRow;
import org.gnome.adw.PreferencesDialog;
import org.gnome.adw.PreferencesGroup;
import org.gnome.adw.Toast;
import org.gnome.gio.Settings;
import org.gnome.glib.GLib;
import org.javagi.base.GErrorException;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/prefs.ui", name = "GrooveboatPrefs")
public class GrooveboatPrefs extends PreferencesDialog {
    private static final Logger logger = LoggerFactory.getLogger(GrooveboatPrefs.class);
    private final SecretKeeper secrets = SecretKeeper.getInstance();
    private final Settings settings = new Settings("io.github.Nyeksenn.groovebox");
    private final ExecutorService exec;
    private final Subsonic subsonic;
    private final UrlValidator validator = new UrlValidator();

    @GtkChild(name = "entry_username")
    public EntryRow usernameEntry;

    @GtkChild(name = "entry_password")
    public EntryRow passwordEntry;

    @GtkChild(name = "entry_url")
    public EntryRow urlEntry;

    @GtkChild(name = "prefs-button-group")
    public PreferencesGroup prefsButtonGroup;

    @GtkCallback(name = "connection-details-changed")
    public void connectionDetailsChanged() {
        prefsButtonGroup.setVisible(true);
    }

    @GtkCallback(name = "connection-details-revert")
    public void connectionDetailsRevert() {
        reloadSavedConnectionDetails();
    }

    private void reloadSavedConnectionDetails() {
        var username = settings.getString("username");
        var url = settings.getString("url");
        var split = url.split("://");
        secrets.lookupPassword(username, split[0], split[1]).thenAccept(pwd -> {
            GLib.idleAddOnce(() -> {
                usernameEntry.setText(username);
                urlEntry.setText(url);
                passwordEntry.setText(pwd);
                prefsButtonGroup.setVisible(false);
                urlEntry.removeCssClass("error");

                subsonic.client.setUsername(username);
                subsonic.client.setPassword(pwd);
                subsonic.client.setBaseUrl(url);
            });
        });
    }

    @GtkCallback(name = "connection-details-save")
    public void connectionDetailsSave() {
        var username = usernameEntry.getText();
        var password = passwordEntry.getText();
        var url = urlEntry.getText();

        exec.submit(() -> {
            if (validator.isValid(url)) {
                var split = url.split("://");
                subsonic.client.setUsername(username);
                subsonic.client.setPassword(password);
                subsonic.client.setBaseUrl(url);

                var serverReachable = false;
                try {
                    // first need to set username for credentials
                    var resp = subsonic.checkCredentials(username);
                    if (resp.status().equals("ok")) {
                        serverReachable = true;
                    }
                } catch (IOException e) {
                    logger.warn("Couldn't reach server");
                }
                if (serverReachable) {
                    try {
                        secrets.storePassword(username, split[0], split[1], password).thenAccept((saved) -> {
                            if (saved) {
                                settings.setString("username", username);
                                settings.setString("url", url);
                            }
                            GLib.idleAddOnce(() -> prefsButtonGroup.setVisible(false));
                        });
                    } catch (GErrorException e) {
                        logger.warn("Couldn't store password");
                        reloadSavedConnectionDetails();
                    }
                } else {
                    GLib.idleAddOnce(() -> {
                        this.addToast(new Toast("Server unreachable. Check settings"));
                    });
                }
            } else {
                GLib.idleAddOnce(() -> {
                    urlEntry.addCssClass("error");
                    this.addToast(new Toast("Invalid URL"));
                });
            }
        });
    }

    @InstanceInit
    public void init() {
    }

    public GrooveboatPrefs(ExecutorService exec, Subsonic subsonic) {
        this.exec = exec;
        this.subsonic = subsonic;
        reloadSavedConnectionDetails();
    }
}