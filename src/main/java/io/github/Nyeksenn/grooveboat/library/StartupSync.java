package io.github.Nyeksenn.grooveboat.library;

import io.github.Nyeksenn.grooveboat.backend.AuthenticationException;
import io.github.Nyeksenn.grooveboat.backend.MusicBackend;
import io.github.Nyeksenn.grooveboat.backend.Session;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * What happens in the background after the app showed the cached library at startup: the stored session is verified
 * with the server and the library is synced. Blocking; run it off the GTK main thread.
 */
public final class StartupSync {
    private static final Logger logger = LoggerFactory.getLogger(StartupSync.class);

    /** How the background check ended. */
    public sealed interface Result permits Synced, Offline, SessionExpired {
    }

    /** The server answered and the library was synced; {@code newAlbums} were new to the cache. */
    public record Synced(int newAlbums) implements Result {
    }

    /** The server couldn't be reached; the cached library stays as it is. */
    public record Offline() implements Result {
    }

    /** The server no longer accepts the stored login; nothing was synced. */
    public record SessionExpired() implements Result {
    }

    private StartupSync() {
    }

    /**
     * @param toVerify the stored session to check with the server, or {@code null} when the session was just
     *                 established and needs no check
     */
    public static Result run(MusicBackend backend, @Nullable Session toVerify, LibrarySync library) {
        if (toVerify != null) {
            try {
                backend.restore(toVerify);
            } catch (AuthenticationException e) {
                logger.warn("Stored session rejected: {}", e.getMessage());
                return new SessionExpired();
            } catch (IOException e) {
                logger.warn("Server unreachable, keeping the cached library: {}", e.getMessage());
                return new Offline();
            }
        }
        return new Synced(library.refresh(false));
    }
}
