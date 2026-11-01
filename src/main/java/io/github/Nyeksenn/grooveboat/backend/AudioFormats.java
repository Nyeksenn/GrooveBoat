package io.github.Nyeksenn.grooveboat.backend;

import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Mapping between codec names (as Jellyfin reports them) and the MIME types stored in the cache. */
public final class AudioFormats {
    private static final Map<String, String> CODEC_TO_MIME = Map.ofEntries(
            Map.entry("flac", "audio/flac"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("aac", "audio/aac"),
            Map.entry("alac", "audio/x-alac"),
            Map.entry("opus", "audio/opus"),
            Map.entry("vorbis", "audio/vorbis"),
            Map.entry("ac3", "audio/ac3"),
            Map.entry("eac3", "audio/eac3"),
            Map.entry("wav", "audio/wav"),
            Map.entry("wma", "audio/x-ms-wma"),
            Map.entry("ape", "audio/x-ape"),
            Map.entry("wavpack", "audio/x-wavpack")
    );

    /** Inverse of {@link #CODEC_TO_MIME}, plus the non-canonical MIME types servers are known to send. */
    private static final Map<String, String> MIME_TO_CODEC = inverseWithAliases();

    /** Codecs that can be copied (not re-encoded) into fMP4 HLS segments. */
    private static final Set<String> FMP4_COPYABLE = Set.of("flac", "mp3", "aac", "alac", "opus", "ac3", "eac3");

    private AudioFormats() {
    }

    private static Map<String, String> inverseWithAliases() {
        var inverse = new HashMap<String, String>();
        CODEC_TO_MIME.forEach((codec, mime) -> inverse.put(mime, codec));
        inverse.put("audio/x-flac", "flac");
        inverse.put("audio/mp3", "mp3");
        return Map.copyOf(inverse);
    }

    /** MIME type for a codec; unknown codecs become {@code audio/x-<codec>}. */
    public static @Nullable String mimeForCodec(@Nullable String codec) {
        if (codec == null || codec.isBlank()) {
            return null;
        }
        var normalized = codec.toLowerCase(Locale.ROOT).trim();
        if (normalized.startsWith("pcm_")) {
            return "audio/wav";
        }
        return CODEC_TO_MIME.getOrDefault(normalized, "audio/x-" + normalized);
    }

    /** Codec for a MIME type, or {@code null} when unknown. Inverse of {@link #mimeForCodec}. */
    public static @Nullable String codecForMime(@Nullable String mime) {
        if (mime == null) {
            return null;
        }
        var normalized = mime.toLowerCase(Locale.ROOT).trim();
        var codec = MIME_TO_CODEC.get(normalized);
        if (codec != null) {
            return codec;
        }
        return normalized.startsWith("audio/x-") ? normalized.substring("audio/x-".length()) : null;
    }

    public static boolean isFmp4Copyable(@Nullable String codec) {
        return codec != null && FMP4_COPYABLE.contains(codec);
    }
}
