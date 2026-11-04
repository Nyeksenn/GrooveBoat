package io.github.Nyeksenn.grooveboat.utils;

import io.github.Nyeksenn.grooveboat.backend.BackendType;
import org.gnome.gio.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/** Keys of the {@code io.github.Nyeksenn.grooveboat} GSettings schema and session-related helpers. */
public final class AppSettings {
    private static final Logger logger = LoggerFactory.getLogger(AppSettings.class);

    public static final String SCHEMA_ID = "io.github.Nyeksenn.grooveboat";
    public static final String BACKEND = "backend";
    public static final String URL = "url";
    public static final String USERNAME = "username";
    public static final String USER_ID = "user-id";
    public static final String DEVICE_ID = "device-id";
    public static final String LAST_SYNC = "last-sync";
    public static final String GAPLESS_PLAYBACK = "gapless-playback";
    public static final String REPLAY_GAIN_ENABLED = "replay-gain-enabled";
    public static final String REPLAY_GAIN_MODE = "replay-gain-mode";
    public static final String REPLAY_GAIN_MODE_TRACK = "track";
    public static final String REPLAY_GAIN_MODE_ALBUM = "album";
    public static final String TRANSCODE_PROFILE_UNMETERED = "transcode-profile-unmetered";
    public static final String TRANSCODE_PROFILE_METERED = "transcode-profile-metered";
    public static final String TRANSCODE_FORMAT = "transcode-format";
    public static final String TRANSCODE_UNSUPPORTED = "transcode-unsupported";

    private AppSettings() {
    }

    private static final class Holder {
        private static final Settings INSTANCE = new Settings(SCHEMA_ID);
    }

    public static Settings get() {
        return Holder.INSTANCE;
    }

    /** The backend of the current session, or empty when logged out. */
    public static Optional<BackendType> backend() {
        return BackendType.fromSettingsValue(get().getString(BACKEND));
    }

    /**
     * One-time migration for installs from before backend selection existed: a configured URL and username mean
     * the session is an OpenSubsonic one.
     */
    public static void migrate() {
        var settings = get();
        if (backend().isEmpty()
                && !settings.getString(URL).isBlank()
                && !settings.getString(USERNAME).isBlank()) {
            logger.info("Migrating existing configuration to the OpenSubsonic backend");
            settings.setString(BACKEND, BackendType.OPENSUBSONIC.settingsValue());
        }
    }

    /** Saves a completed login. */
    public static void saveSession(BackendType type, String url, String username, String userId) {
        var settings = get();
        settings.setString(URL, url);
        settings.setString(USERNAME, username);
        settings.setString(USER_ID, userId == null ? "" : userId);
        settings.setUint64(LAST_SYNC, 0L);
        // Written last: a non-"none" backend is what marks setup as complete.
        settings.setString(BACKEND, type.settingsValue());
    }

    /** Resets all session keys. The device id is kept on purpose. */
    public static void resetSession() {
        var settings = get();
        settings.reset(BACKEND);
        settings.reset(URL);
        settings.reset(USERNAME);
        settings.reset(USER_ID);
        settings.reset(LAST_SYNC);
    }
}
