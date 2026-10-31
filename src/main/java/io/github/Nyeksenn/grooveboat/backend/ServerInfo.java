package io.github.Nyeksenn.grooveboat.backend;

/**
 * Result of probing a server URL.
 *
 * @param compatible whether a supported server of the probed backend type answered
 * @param reason     user-facing explanation when not compatible, otherwise {@code null}
 */
public record ServerInfo(boolean compatible, String name, String version, String reason) {
    public static ServerInfo compatible(String name, String version) {
        return new ServerInfo(true, name, version, null);
    }

    public static ServerInfo incompatible(String reason) {
        return new ServerInfo(false, null, null, reason);
    }
}
