package io.github.Nyeksenn.grooveboat.backend;

import org.jspecify.annotations.Nullable;

/**
 * A logged-in session.
 *
 * @param credential the secret kept in the keyring: the password for OpenSubsonic, the access token for Jellyfin
 * @param userId     server-side user id (Jellyfin); empty for OpenSubsonic
 */
public record Session(String username, @Nullable String credential, @Nullable String userId) {
    @Override
    public String toString() {
        return "Session[username=" + username + ", userId=" + userId + "]";
    }
}
