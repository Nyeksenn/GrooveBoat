package io.github.Nyeksenn.grooveboat.utils;

import java.util.HashMap;
import java.util.Map;

public final class Helpers {
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
        map.put("audio/wav", "audio/x-wav");
        map.put("audio/x-wav", "audio/x-wav");
        map.put("audio/wave", "audio/x-wav");
        map.put("audio/vnd.wave", "audio/x-wav");
        map.put("audio/speex", "audio/x-speex");
        map.put("audio/x-ms-wma", "audio/x-wma");
        map.put("audio/basic", "audio/x-mulaw");

        // Video formats
        map.put("video/h264", "video/x-h264");


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
