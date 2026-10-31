package io.github.Nyeksenn.grooveboat.utils;

import io.ebean.Database;
import io.ebean.Transaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

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

    public static final List<String> TABLES = Arrays.asList(
            "PUBLIC.ALBUM",
            "PUBLIC.ALBUM_ARTISTS",
            "PUBLIC.ARTIST",
            "PUBLIC.SONG",
            "PUBLIC.SONG_ARTISTS"
    );

    public static void truncateDB(Database db) throws SQLException {
        try (Transaction transaction = db.beginTransaction()) {

            Connection connection = transaction.connection();

            try (Statement stmt = connection.createStatement()) {

                stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");

                for (String tbl : TABLES) {
                    String sql = "TRUNCATE TABLE " + tbl + " RESTART IDENTITY";
                    stmt.execute(sql);
                }

                stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }

            connection.commit();

            db.cacheManager().clearAll();

            transaction.commit();
        }

    }
}
