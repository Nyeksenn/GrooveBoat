package io.github.Nyeksenn.grooveboat.backend;

import org.jspecify.annotations.Nullable;

/**
 * Where to play a song from.
 *
 * @param uri           URI handed to the GStreamer pipeline; contains credentials, so never log it
 * @param hls           whether {@code uri} is an HLS playlist (as opposed to a progressive download)
 * @param playSessionId server-side play session id, or {@code null}
 * @param quality       what the source delivers, or {@code null} when unknown
 */
public record PlaybackSource(String uri, boolean hls, @Nullable String playSessionId,
                             @Nullable StreamQuality quality) {
    public PlaybackSource(String uri, boolean hls, @Nullable String playSessionId) {
        this(uri, hls, playSessionId, null);
    }

    @Override
    public String toString() {
        return "PlaybackSource[uri=" + redact(uri) + ", hls=" + hls + "]";
    }

    /** Strips the query string, which carries credentials, so the URI can be logged. */
    public static @Nullable String redact(@Nullable String uri) {
        if (uri == null) {
            return null;
        }
        var idx = uri.indexOf('?');
        return idx < 0 ? uri : uri.substring(0, idx) + "?<redacted>";
    }
}
