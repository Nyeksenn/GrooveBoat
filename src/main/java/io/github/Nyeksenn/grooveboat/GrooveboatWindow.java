package io.github.Nyeksenn.grooveboat;

import io.github.jwharm.javagi.gobject.annotations.InstanceInit;
import io.github.jwharm.javagi.gtk.annotations.GtkTemplate;
import org.gnome.adw.Window;
import org.gnome.gio.Settings;
import org.gnome.gobject.ParamSpec;


@GtkTemplate(ui = "/io/github/Nyeksenn/grooveboat/window.ui", name = "GrooveboatWindow")
public class GrooveboatWindow extends Window {
    public Settings settings;

    public GrooveboatWindow(GrooveboatApp app) {
        setApplication(app);
    }

    public void handleConnectionPrefChange(ParamSpec pspec) {
//        var name = pspec.getName();
//        switch (name) {
//            case "username":
//                var username = settings.getString("username");
//                subsonicPreferences.setUsername(username);
//                subsonic = new Subsonic(subsonicPreferences);
//                break;
//            case "password":
//                var password = settings.getString("password");
//                subsonicPreferences.setPassword(password);
//                subsonic = new Subsonic(subsonicPreferences);
//                break;
//            case "url":
//                var url = settings.getString("url");
//                subsonicPreferences.setServerUrl(url);
//                subsonic = new Subsonic(subsonicPreferences);
//                break;
//        }
    }

    @InstanceInit
    public void init() {

//        settings = new Settings("io.github.Nyeksenn.groovebox");
//        settings.bind("username", subsonicConnectionDetails, "username", SettingsBindFlags.DEFAULT);
//        settings.bind("password", subsonicConnectionDetails, "password", SettingsBindFlags.DEFAULT);
//        settings.bind("url", subsonicConnectionDetails, "url", SettingsBindFlags.DEFAULT);
//
//        var username = settings.getString("username");
//        var password = settings.getString("password");
//        var url = settings.getString("url");
//        subsonicPreferences = new SubsonicPreferences(url, username, password);
//        subsonicConnectionDetails.onNotify(null, this::handleConnectionPrefChange);
    }
}
