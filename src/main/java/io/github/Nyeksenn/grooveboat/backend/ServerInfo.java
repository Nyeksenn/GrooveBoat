package io.github.Nyeksenn.grooveboat.backend;

import org.jspecify.annotations.Nullable;

/** Result of probing a server URL. */
public sealed interface ServerInfo {
    /** A supported server of the probed backend type answered. */
    record Compatible(String name, String version) implements ServerInfo {
    }

    /**
     * No usable server answered.
     *
     * @param reason user-facing explanation, if there is one
     */
    record Incompatible(@Nullable String reason) implements ServerInfo {
    }

    static ServerInfo compatible(String name, String version) {
        return new Compatible(name, version);
    }

    static ServerInfo incompatible(String reason) {
        return new Incompatible(reason);
    }
}
