package io.github.Nyeksenn.grooveboat.backend;

import io.github.Nyeksenn.grooveboat.backend.jellyfin.JellyfinBackend;
import io.github.Nyeksenn.grooveboat.backend.subsonic.SubsonicBackend;
import io.github.Nyeksenn.grooveboat.utils.AppSettings;

import java.util.UUID;

import static org.gnome.glib.GLib.getHostName;

/** Creates backend instances for a URL. */
public final class BackendFactory {
    private static final String APP_VERSION = "1.0";

    private BackendFactory() {
    }

    public static MusicBackend create(BackendType type, String baseUrl) {
        return switch (type) {
            case OPENSUBSONIC -> new SubsonicBackend(baseUrl);
            case JELLYFIN -> new JellyfinBackend(baseUrl, deviceId(), "GrooveBoat on " + getHostName(), APP_VERSION);
        };
    }

    /** Stable per-installation device id. Created on first use and kept across logouts. */
    private static synchronized String deviceId() {
        var settings = AppSettings.get();
        var id = settings.getString(AppSettings.DEVICE_ID);
        if (id.isBlank()) {
            id = "grooveboat-" + UUID.randomUUID().toString().replace("-", "");
            settings.setString(AppSettings.DEVICE_ID, id);
        }
        return id;
    }
}
