package io.github.Nyeksenn.grooveboat.backend;

import java.util.Locale;

/** How much quality the user wants to stream. {@link #ORIGINAL} means no transcoding. */
public enum TranscodeProfile {
    ORIGINAL("original", 0),
    HIGH("high", 320),
    BALANCED("balanced", 192),
    DATA_SAVER("data-saver", 96);

    private final String key;
    private final int maxBitrateKbps;

    TranscodeProfile(String key, int maxBitrateKbps) {
        this.key = key;
        this.maxBitrateKbps = maxBitrateKbps;
    }

    /** The GSettings value. */
    public String key() {
        return key;
    }

    /** Bitrate cap in kbit/s; 0 for {@link #ORIGINAL}. */
    public int maxBitrateKbps() {
        return maxBitrateKbps;
    }

    public boolean transcodes() {
        return this != ORIGINAL;
    }

    /** Parses a GSettings value; anything unknown means {@link #ORIGINAL}. */
    public static TranscodeProfile fromKey(String key) {
        var normalized = key == null ? "" : key.toLowerCase(Locale.ROOT).trim();
        for (var profile : values()) {
            if (profile.key.equals(normalized)) {
                return profile;
            }
        }
        return ORIGINAL;
    }
}
