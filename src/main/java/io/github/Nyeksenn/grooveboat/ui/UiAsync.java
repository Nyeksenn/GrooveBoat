package io.github.Nyeksenn.grooveboat.ui;

import org.gnome.glib.GLib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The "work on a pool thread, show the result on the GTK main thread" pattern. Failures are logged instead of
 * disappearing into a dropped {@code Future}.
 * <p>
 * The work runs on the given executor, so custom GObjects it creates are made on a pool thread and never on the
 * ForkJoin common pool (see {@code docs/architecture/threading.md}).
 */
final class UiAsync {
    private static final Logger logger = LoggerFactory.getLogger(UiAsync.class);
    private static final Executor MAIN_LOOP = task -> GLib.idleAddOnce(task::run);

    private UiAsync() {
    }

    /** Runs {@code work} on {@code exec}, then {@code onUi} with its result on the main thread. */
    static <T> void run(Executor exec, Supplier<T> work, Consumer<T> onUi) {
        run(exec, work, onUi, MAIN_LOOP);
    }

    /** Like {@link #run(Executor, Supplier, Consumer)}, with the hand-over to the main thread made explicit. */
    static <T> void run(Executor exec, Supplier<T> work, Consumer<T> onUi, Executor ui) {
        background(exec, () -> {
            var result = work.get();
            ui.execute(() -> {
                try {
                    onUi.accept(result);
                } catch (RuntimeException e) {
                    logger.error("Updating the UI failed", e);
                }
            });
        });
    }

    /** Runs {@code task} on {@code exec} and logs whatever it throws. */
    static void background(Executor exec, Runnable task) {
        exec.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                logger.error("Background task failed", t);
            }
        });
    }

    /**
     * Loads that replace each other: only the newest result is shown, so overlapping reloads can't add items twice.
     * Start loads from the main thread.
     */
    static final class Latest {
        private int generation = 0;

        <T> void run(Executor exec, Supplier<T> work, Consumer<T> onUi) {
            run(exec, work, onUi, MAIN_LOOP);
        }

        <T> void run(Executor exec, Supplier<T> work, Consumer<T> onUi, Executor ui) {
            var mine = ++generation;
            UiAsync.run(exec, work, result -> {
                if (mine == generation) {
                    onUi.accept(result);
                }
            }, ui);
        }
    }
}
