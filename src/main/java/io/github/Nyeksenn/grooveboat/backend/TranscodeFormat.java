package io.github.Nyeksenn.grooveboat.backend;

import java.util.Locale;

/** Codec the server should encode to when it transcodes. {@link #AUTO} lets the backend pick a safe default. */
public enum TranscodeFormat {
    AUTO("auto"),
    OPUS("opus"),
    AAC("aac"),
    MP3("mp3");

    private final String key;

    TranscodeFormat(String key) {
        this.key = key;
    }

    /** The GSettings value. */
    public String key() {
        return key;
    }

    /** The codec name for the server, with {@link #AUTO} replaced by {@code autoChoice}. */
    public String codec(TranscodeFormat autoChoice) {
        return (this == AUTO ? autoChoice : this).key;
    }

    /** Parses a GSettings value; anything unknown means {@link #AUTO}. */
    public static TranscodeFormat fromKey(String key) {
        var normalized = key == null ? "" : key.toLowerCase(Locale.ROOT).trim();
        for (var format : values()) {
            if (format.key.equals(normalized)) {
                return format;
            }
        }
        return AUTO;
    }
}
