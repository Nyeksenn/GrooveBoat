package io.github.Nyeksenn.grooveboat.ui;

import io.github.Nyeksenn.grooveboat.gobjects.SecretKeeper;
import org.gnome.adw.EntryRow;
import org.gnome.adw.PreferencesDialog;
import org.gnome.gio.Settings;
import org.gnome.gio.SettingsBindFlags;
import org.gnome.gtk.Editable;
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

    @GtkChild(name = "entry_username")
    public EntryRow username;

    @GtkChild(name = "entry_password")
    public EntryRow password;

    @GtkChild(name = "entry_url")
    public EntryRow url;

    @GtkCallback(name = "password-changed-handler")
    public void passwordChangedHandler(Editable entry) {
        var split = url.getText().split("://");
        try {
            secrets.storePassword(username.getText(), split[0], split[1], entry.getText());
        } catch (GErrorException e) {
            logger.warn("Couldn't store password");
        }
    }

    Settings settings;

    @InstanceInit
    public void init() {
        settings = new Settings("io.github.Nyeksenn.groovebox");
        settings.bind("username", username, "text", SettingsBindFlags.DEFAULT);
        //settings.bind("password", password, "text", SettingsBindFlags.DEFAULT);
        settings.bind("url", url, "text", SettingsBindFlags.DEFAULT);
    }

    public GrooveboatPrefs() {
    }
}