package io.github.Nyeksenn.grooveboat.ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The app's thread pools, in one place. See {@code docs/architecture/threading.md} for what may run where; in short,
 * network, database and keyring work stays off the GTK main thread and returns to it with {@code GLib.idleAddOnce}.
 */
public final class AppExecutors {
    /** Blocking work: network, database and keyring. */
    public final ExecutorService io = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    /** Delayed and periodic tasks: the position ticker and the seek debounce. */
    public final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    /** Hosts the GStreamer main loop for the app's lifetime. */
    public final ExecutorService gst = Executors.newSingleThreadExecutor();

    /** Stops the pools that run app work. The GStreamer thread is stopped separately, after its loop has quit. */
    public void shutdownWorkers() {
        io.shutdownNow();
        scheduler.shutdownNow();
    }

    public void shutdownGst() {
        gst.shutdownNow();
    }
}
