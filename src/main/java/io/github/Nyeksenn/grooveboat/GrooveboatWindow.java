package io.github.Nyeksenn.grooveboat;

import io.github.jwharm.javagi.gobject.annotations.InstanceInit;
import io.github.jwharm.javagi.gtk.annotations.GtkTemplate;
import net.beardbot.subsonic.client.Subsonic;
import net.beardbot.subsonic.client.SubsonicPreferences;
import net.beardbot.subsonic.client.base.SubsonicIncompatibilityException;
import org.gnome.adw.Window;
import org.gnome.gio.Settings;
import org.gnome.gio.SettingsBindFlags;
import org.gnome.gobject.ParamSpec;


@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/window.ui", name = "GrooveboatWindow")
public class GrooveboatWindow extends Window {
    public Settings settings;

    public Subsonic subsonic;

    public SubsonicConnectionDetails subsonicConnectionDetails;

    public SubsonicPreferences subsonicPreferences;

    public GrooveboatWindow(GrooveboatApp app) {
        setApplication(app);
    }

    public void handleConnectionPrefChange(ParamSpec pspec) {
        var name = pspec.getName();
        switch (name) {
            case "username":
                var username = settings.getString("username");
                subsonicPreferences.setUsername(username);
                subsonic = new Subsonic(subsonicPreferences);
                break;
            case "password":
                var password = settings.getString("password");
                subsonicPreferences.setPassword(password);
                subsonic = new Subsonic(subsonicPreferences);
                break;
            case "url":
                var url = settings.getString("url");
                subsonicPreferences.setServerUrl(url);
                subsonic = new Subsonic(subsonicPreferences);
                break;
        }
    }

    @InstanceInit
    public void init() {
        subsonicConnectionDetails = new SubsonicConnectionDetails();

        settings = new Settings("io.github.Nyeksenn.groovebox");
        settings.bind("username", subsonicConnectionDetails, "username", SettingsBindFlags.DEFAULT);
        settings.bind("password", subsonicConnectionDetails, "password", SettingsBindFlags.DEFAULT);
        settings.bind("url", subsonicConnectionDetails, "url", SettingsBindFlags.DEFAULT);

        var username = settings.getString("username");
        var password = settings.getString("password");
        var url = settings.getString("url");
        subsonicPreferences = new SubsonicPreferences(url, username, password);
        subsonicConnectionDetails.onNotify(null, this::handleConnectionPrefChange);

        subsonic = new Subsonic(subsonicPreferences);

        try {
            if (subsonic.testConnection()) {
                System.out.println("Succesfully connected to server!");
            } else {
                System.out.println("Failed to connect to server!");
            }
        } catch (SubsonicIncompatibilityException e) {
            System.out.println("The server is not compatible with the client! Please upgrade you server!");
        }
    }
}
