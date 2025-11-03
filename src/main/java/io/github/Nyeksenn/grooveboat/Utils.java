package io.github.Nyeksenn.grooveboat;

import java.util.HashMap;
import java.util.Map;

public final class Utils {
    public static String getUserDataPath() {
        String os = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (os.contains("win")) {
            // Windows: Use APPDATA environment variable, fallback to user.home\AppData\Roaming
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                return appData;
            }
            return userHome + "\\AppData\\Roaming";

        } else if (os.contains("mac") || os.contains("darwin")) {
            // macOS: Use ~/Library/Application Support
            return userHome + "/Library/Application Support";

        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            // Linux/Unix: Use XDG_DATA_HOME if set, otherwise ~/.local/share
            String xdgData = System.getenv("XDG_DATA_HOME");
            if (xdgData != null && !xdgData.isEmpty()) {
                return xdgData;
            }
            return userHome + "/.local/share";

        } else {
            throw new RuntimeException("Unsupported operating system: " + os);
        }
    }

    private static final Map<String, String> IANA_TO_GSTREAMER;

    static {
        Map<String, String> map = new HashMap<>(128); // Pre-sized for efficiency

        // Audio formats
        map.put("audio/flac", "audio/x-flac");
        map.put("audio/vorbis", "audio/x-vorbis");
        map.put("audio/opus", "audio/x-opus");
        map.put("audio/wav", "audio/x-wav");
        map.put("audio/x-wav", "audio/x-wav");
        map.put("audio/wave", "audio/x-wav");
        map.put("audio/vnd.wave", "audio/x-wav");
        map.put("audio/speex", "audio/x-speex");
        map.put("audio/mpeg", "audio/mpeg");
        map.put("audio/mp3", "audio/mpeg");
        map.put("audio/aac", "audio/mpeg");
        map.put("audio/aacp", "audio/mpeg");
        map.put("audio/mp4", "audio/x-m4a");
        map.put("audio/x-m4a", "audio/x-m4a");
        map.put("audio/ogg", "audio/x-vorbis");
        map.put("audio/webm", "audio/x-vorbis");
        map.put("audio/x-ms-wma", "audio/x-wma");
        map.put("audio/x-aiff", "audio/x-aiff");
        map.put("audio/aiff", "audio/x-aiff");
        map.put("audio/basic", "audio/x-mulaw");
        map.put("audio/x-matroska", "audio/x-matroska");
        map.put("audio/amr", "audio/AMR");
        map.put("audio/amr-wb", "audio/AMR-WB");

        // Video formats
        map.put("video/mp4", "video/quicktime");
        map.put("video/quicktime", "video/quicktime");
        map.put("video/x-msvideo", "video/x-msvideo");
        map.put("video/avi", "video/x-msvideo");
        map.put("video/x-matroska", "video/x-matroska");
        map.put("video/mkv", "video/x-matroska");
        map.put("video/webm", "video/webm");
        map.put("video/ogg", "video/x-theora");
        map.put("video/theora", "video/x-theora");
        map.put("video/x-flv", "video/x-flv");
        map.put("video/x-ms-wmv", "video/x-ms-wmv");
        map.put("video/x-ms-asf", "video/x-ms-asf");
        map.put("video/mpeg", "video/mpeg");
        map.put("video/3gpp", "video/3gpp");
        map.put("video/3gpp2", "video/3gpp2");

        // Image formats
        map.put("image/jpeg", "image/jpeg");
        map.put("image/jpg", "image/jpeg");
        map.put("image/png", "image/png");
        map.put("image/gif", "image/gif");
        map.put("image/bmp", "image/bmp");
        map.put("image/x-bmp", "image/bmp");
        map.put("image/x-ms-bmp", "image/bmp");
        map.put("image/tiff", "image/tiff");
        map.put("image/webp", "image/webp");
        map.put("image/svg+xml", "image/svg+xml");

        // Application/container formats
        map.put("application/ogg", "application/ogg");
        map.put("application/x-ogg", "application/ogg");

        IANA_TO_GSTREAMER = Map.copyOf(map); // Java 10+ immutable copy
    }

    public static String toGStreamerType(String ianaType) {
        if (ianaType == null) {
            return null;
        }

        // Normalize to lowercase for case-insensitive matching
        String normalized = ianaType.toLowerCase().trim();

        // Return mapped type or original if no mapping exists
        return IANA_TO_GSTREAMER.getOrDefault(normalized, ianaType);
    }

    public static boolean hasMapping(String ianaType) {
        return ianaType != null && IANA_TO_GSTREAMER.containsKey(ianaType.toLowerCase().trim());
    }
}
