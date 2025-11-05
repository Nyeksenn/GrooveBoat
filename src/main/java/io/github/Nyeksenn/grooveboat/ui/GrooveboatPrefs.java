package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.gobjects.SecretKeeper;
import org.gnome.adw.EntryRow;
import org.gnome.adw.PreferencesDialog;
import org.gnome.adw.PreferencesGroup;
import org.gnome.gio.Settings;
import org.gnome.glib.GLib;
import org.javagi.base.GErrorException;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkCallback;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/prefs.ui", name = "GrooveboatPrefs")
public class GrooveboatPrefs extends PreferencesDialog {
    private static final Logger logger = LoggerFactory.getLogger(GrooveboatPrefs.class);
    private final SecretKeeper secrets = SecretKeeper.getInstance();
    private final Settings settings = new Settings("io.github.Nyeksenn.groovebox");

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
            });
        });
    }

    @GtkCallback(name = "connection-details-save")
    public void connectionDetailsSave() {
        var username = usernameEntry.getText();
        var password = passwordEntry.getText();
        var url = urlEntry.getText();

        // TODO: Validate credentials working
        // TODO: If change client

        var split = url.split("://");
        if (split.length == 2) {
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
        }
    }

    @InstanceInit
    public void init() {
    }

    public GrooveboatPrefs() {
        reloadSavedConnectionDetails();
    }
}