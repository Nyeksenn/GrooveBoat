package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.adw.EntryRow;
import org.gnome.adw.PreferencesDialog;
import org.gnome.gio.Settings;
import org.gnome.gio.SettingsBindFlags;
import org.javagi.gobject.annotations.InstanceInit;
import org.javagi.gtk.annotations.GtkChild;
import org.javagi.gtk.annotations.GtkTemplate;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/prefs.ui", name = "GrooveboatPrefs")
public class GrooveboatPrefs extends PreferencesDialog {
    @GtkChild(name = "entry_username")
    public EntryRow username;

    @GtkChild(name = "entry_password")
    public EntryRow password;

    @GtkChild(name = "entry_url")
    public EntryRow url;

    Settings settings;

    @InstanceInit
    public void init() {
        settings = new Settings("io.github.Nyeksenn.groovebox");
        settings.bind("username", username, "text", SettingsBindFlags.DEFAULT);
        settings.bind("password", password, "text", SettingsBindFlags.DEFAULT);
        settings.bind("url", url, "text", SettingsBindFlags.DEFAULT);
    }

    public GrooveboatPrefs() {
    }
}