package io.github.Nyeksenn.grooveboat.backend;

import java.util.Arrays;
import java.util.Optional;

/**
 * The kinds of music server GrooveBoat can talk to. The {@link #settingsValue()} is what is stored in the
 * {@code backend} GSettings key; {@code "none"} there means "logged out".
 */
public enum BackendType {
    OPENSUBSONIC("opensubsonic", "OpenSubsonic"),
    JELLYFIN("jellyfin", "Jellyfin");

    public static final String NONE = "none";

    private final String settingsValue;
    private final String displayName;

    BackendType(String settingsValue, String displayName) {
        this.settingsValue = settingsValue;
        this.displayName = displayName;
    }

    public String settingsValue() {
        return settingsValue;
    }

    public String displayName() {
        return displayName;
    }

    /** Codec the server encodes to when the user leaves the transcoding format on Auto. */
    public TranscodeFormat defaultTranscodeFormat() {
        return this == JELLYFIN ? TranscodeFormat.OPUS : TranscodeFormat.MP3;
    }

    public BackendType other() {
        return this == OPENSUBSONIC ? JELLYFIN : OPENSUBSONIC;
    }

    public static Optional<BackendType> fromSettingsValue(String value) {
        return Arrays.stream(values()).filter(t -> t.settingsValue.equals(value)).findFirst();
    }
}
