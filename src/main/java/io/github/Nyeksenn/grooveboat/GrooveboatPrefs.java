package io.github.Nyeksenn.grooveboat;

import org.gnome.adw.PreferencesDialog;
import org.gnome.gio.Settings;

import io.github.jwharm.javagi.gobject.annotations.InstanceInit;
import io.github.jwharm.javagi.gtk.annotations.GtkTemplate;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/prefs.ui")
public class GrooveboatPrefs extends PreferencesDialog {
    Settings settings;

    @InstanceInit
    public void init() {
        settings = new Settings("io.github.Nyeksenn.groovebox");
    }

    public GrooveboatPrefs() {
    }
}