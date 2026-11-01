package io.github.Nyeksenn.grooveboat.ui;

import org.javagi.gobject.SignalConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Things a widget set up while it was realized and must undo when it is unrealized, typically signal handlers and
 * listeners on app-lifetime objects (the queue store, its selection). A widget is realized again whenever the window
 * crosses the size breakpoint, so the same scope is filled and released many times.
 */
final class RealizedScope {
    private static final Logger logger = LoggerFactory.getLogger(RealizedScope.class);

    private final List<Runnable> cleanups = new ArrayList<>();

    /** Runs {@code cleanup} when the scope is released. */
    void onRelease(Runnable cleanup) {
        cleanups.add(cleanup);
    }

    /** Disconnects {@code connection} when the scope is released. Returns it for convenience. */
    <C extends SignalConnection<?>> C track(C connection) {
        onRelease(connection::disconnect);
        return connection;
    }

    /** Runs all cleanups, last registered first. One failing cleanup doesn't stop the others. */
    void release() {
        for (var cleanup : cleanups.reversed()) {
            try {
                cleanup.run();
            } catch (RuntimeException e) {
                logger.warn("Cleanup on unrealize failed", e);
            }
        }
        cleanups.clear();
    }
}
