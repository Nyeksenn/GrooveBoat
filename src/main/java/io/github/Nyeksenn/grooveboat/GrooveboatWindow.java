package io.github.Nyeksenn.grooveboat;

import io.github.Nyeksenn.grooveboat.client.ConnDetails;
import io.github.jwharm.javagi.gobject.annotations.InstanceInit;
import io.github.jwharm.javagi.gobject.annotations.RegisteredType;
import io.github.jwharm.javagi.gtk.annotations.GtkTemplate;
import org.gnome.adw.Window;
import org.gnome.gio.Settings;
import org.gnome.gio.SettingsBindFlags;
import org.gnome.gobject.ParamSpec;

@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/window.ui", name = "GrooveboatWindow")
public class GrooveboatWindow extends Window {

    public Settings settings;

    public ConnDetails connDetails;

    public GrooveboatWindow(GrooveboatApp app) {
        setApplication(app);
    }

    // Reset Client if connection details change
    public void handleConnectionPrefChange(ParamSpec pspec) {
        var app = (GrooveboatApp) getApplication();
        var client = app.client;
        var name = pspec.getName();
        switch (name) {
            case "username":
                var username = settings.getString("username");
                client.setUsername(username);
                break;
            case "password":
                var password = settings.getString("password");
                client.setPassword(password);
                break;
            case "url":
                var url = settings.getString("url");
                client.setBaseUrl(url);
                break;
        }
    }

    @InstanceInit
    public void init() {
        connDetails = new ConnDetails();

        settings = new Settings("io.github.Nyeksenn.groovebox");
        settings.bind("username", connDetails, "username", SettingsBindFlags.DEFAULT);
        settings.bind("password", connDetails, "password", SettingsBindFlags.DEFAULT);
        settings.bind("url", connDetails, "url", SettingsBindFlags.DEFAULT);
        connDetails.onNotify(null, this::handleConnectionPrefChange);
    }
}
