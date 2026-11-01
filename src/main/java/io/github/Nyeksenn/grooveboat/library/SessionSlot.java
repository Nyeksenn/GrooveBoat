package io.github.Nyeksenn.grooveboat.library;

import io.github.Nyeksenn.grooveboat.backend.MusicBackend;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The app's session slot: one {@link MusicBackend} together with the {@link LibrarySync} built on it. They are filled
 * once by {@link #start} and emptied only by {@link #end}; there is deliberately no way to swap the backend or server
 * of a running session. Thread-safe: backend and library are always read as a consistent pair.
 */
public final class SessionSlot {
    private static final Logger logger = LoggerFactory.getLogger(SessionSlot.class);

    /** A backend and its sync; they come and go together. */
    public record Active(MusicBackend backend, LibrarySync library) {
    }

    private final AtomicReference<Active> active = new AtomicReference<>();

    /**
     * Fills the slot.
     *
     * @throws IllegalStateException when a session is already active; log out first
     */
    public void start(MusicBackend backend) {
        if (active.get() != null) {
            throw alreadyActive();
        }
        var next = new Active(backend, new LibrarySync(backend));
        if (!active.compareAndSet(null, next)) {
            next.library().close();
            throw alreadyActive();
        }
        logger.info("Starting {} session for {}", backend.type().displayName(), backend.host());
    }

    private static IllegalStateException alreadyActive() {
        return new IllegalStateException("A session is already active; log out first");
    }

    public boolean isActive() {
        return active.get() != null;
    }

    /** The current backend and sync as a pair, or {@code null} when logged out. */
    public @Nullable Active current() {
        return active.get();
    }

    /** The current session's backend, or {@code null} when logged out. */
    public @Nullable MusicBackend backend() {
        var current = active.get();
        return current == null ? null : current.backend();
    }

    /** The current session's library sync, or {@code null} when logged out. */
    public @Nullable LibrarySync library() {
        var current = active.get();
        return current == null ? null : current.library();
    }

    /** Empties the slot and closes the sync and the backend. Does nothing when no session is active. */
    public void end() {
        var old = active.getAndSet(null);
        if (old != null) {
            old.library().close();
            old.backend().close();
        }
    }
}
