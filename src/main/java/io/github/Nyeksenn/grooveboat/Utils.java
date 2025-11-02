package io.github.Nyeksenn.grooveboat;

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
}
