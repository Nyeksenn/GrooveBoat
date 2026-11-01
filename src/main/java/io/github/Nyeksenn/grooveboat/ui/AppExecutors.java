package io.github.Nyeksenn.grooveboat.ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The app's thread pools, in one place. See {@code docs/architecture/threading.md} for what may run where; in short,
 * network, database and keyring work stays off the GTK main thread and returns to it with {@code GLib.idleAddOnce}.
 */
public final class AppExecutors {
    /**
     * Blocking work: network, database and keyring. A virtual thread per task, so slow requests never queue behind
     * each other or starve a task the user is waiting for. Custom GObjects created on these threads behave like ones
     * created on platform threads (checked with java-gi 1.0.0-RC3: 330,000 list items built on virtual threads and
     * appended on the main loop under load, with GC, without a failure).
     */
    public final ExecutorService io =
            Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("grooveboat-io-", 0).factory());
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
