package io.github.Nyeksenn.grooveboat.backend;

import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * What a playback source delivers, for display.
 *
 * @param codec      codec name, e.g. {@code flac}
 * @param kbps       bitrate in kbit/s; 0 when unknown
 * @param transcoded whether the server re-encodes the song (as opposed to sending the original)
 */
public record StreamQuality(String codec, int kbps, boolean transcoded) {
    /** Short text for a pill, e.g. {@code FLAC · 987 kbps}; just the codec when the bitrate is unknown. */
    public String label() {
        var name = codec.toUpperCase(Locale.ROOT);
        return kbps > 0 ? name + " · " + kbps + " kbps" : name;
    }

    /**
     * The quality of the original file, or {@code null} when its codec is unknown.
     *
     * @param codec codec of the stored content type, or {@code null}
     */
    public static @Nullable StreamQuality original(@Nullable String codec, int kbps) {
        return codec == null ? null : new StreamQuality(codec, Math.max(0, kbps), false);
    }

    /**
     * The quality after a transcode request. The original passes through when {@code mayCopy} and it already fits
     * under {@code capKbps}; otherwise the server re-encodes to {@code target} at the cap.
     */
    public static StreamQuality transcoded(@Nullable String sourceCodec, int sourceKbps, boolean mayCopy,
                                           String target, int capKbps) {
        if (mayCopy && sourceCodec != null && sourceKbps > 0 && sourceKbps <= capKbps) {
            return new StreamQuality(sourceCodec, sourceKbps, false);
        }
        return new StreamQuality(target, capKbps, true);
    }
}
