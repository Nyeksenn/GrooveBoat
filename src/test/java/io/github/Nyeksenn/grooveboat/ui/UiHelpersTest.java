package io.github.Nyeksenn.grooveboat.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

class UiHelpersTest {
    /** Collects tasks so a test decides when (and in which order) they run. */
    private static final class ManualExecutor implements Executor {
        final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runAll() {
            while (!tasks.isEmpty()) {
                tasks.remove().run();
            }
        }
    }

    private final ManualExecutor pool = new ManualExecutor();
    private final ManualExecutor ui = new ManualExecutor();

    @Test
    void scopeReleasesInReverseOrderAndCanBeReused() {
        var scope = new RealizedScope();
        var calls = new ArrayList<String>();
        scope.onRelease(() -> calls.add("first"));
        scope.onRelease(() -> calls.add("second"));

        scope.release();
        assertThat(calls).containsExactly("second", "first");

        scope.release();
        assertThat(calls).hasSize(2);

        scope.onRelease(() -> calls.add("again"));
        scope.release();
        assertThat(calls).containsExactly("second", "first", "again");
    }

    @Test
    void failingCleanupDoesNotStopTheOthers() {
        var scope = new RealizedScope();
        var calls = new ArrayList<String>();
        scope.onRelease(() -> calls.add("last to run"));
        scope.onRelease(() -> {
            throw new IllegalStateException("boom");
        });
        scope.onRelease(() -> calls.add("first to run"));

        scope.release();

        assertThat(calls).containsExactly("first to run", "last to run");
    }

    @Test
    void resultIsDeliveredOnTheUiExecutorOnly() {
        var shown = new ArrayList<String>();

        UiAsync.run(pool, () -> "result", shown::add, ui);
        assertThat(shown).isEmpty();

        pool.runAll();
        assertThat(shown).isEmpty();

        ui.runAll();
        assertThat(shown).containsExactly("result");
    }

    @Test
    void failingWorkIsContainedAndShowsNothing() {
        var shown = new ArrayList<String>();

        UiAsync.<String>run(pool, () -> {
            throw new IllegalStateException("boom");
        }, shown::add, ui);
        pool.runAll();
        ui.runAll();

        assertThat(shown).isEmpty();
    }

    @Test
    void failingUiUpdateIsContained() {
        UiAsync.run(pool, () -> "result", _ -> {
            throw new IllegalStateException("boom");
        }, ui);
        pool.runAll();

        ui.runAll();
    }

    @Test
    void backgroundContainsFailures() {
        UiAsync.background(pool, () -> {
            throw new IllegalStateException("boom");
        });

        pool.runAll();
    }

    @Test
    void onlyTheNewestOverlappingLoadIsShown() {
        var latest = new UiAsync.Latest();
        var shown = new ArrayList<String>();

        latest.run(pool, () -> "old", shown::add, ui);
        latest.run(pool, () -> "new", shown::add, ui);
        pool.runAll();
        ui.runAll();

        assertThat(shown).containsExactly("new");
    }

    @Test
    void aLoadStartedAfterAnotherFinishedIsShownToo() {
        var latest = new UiAsync.Latest();
        var shown = new ArrayList<String>();

        latest.run(pool, () -> "first", shown::add, ui);
        pool.runAll();
        ui.runAll();
        latest.run(pool, () -> "second", shown::add, ui);
        pool.runAll();
        ui.runAll();

        assertThat(shown).containsExactly("first", "second");
    }

    @Test
    void aSupersededLoadCompletingLastStillDoesNotWin() {
        var latest = new UiAsync.Latest();
        List<String> shown = new ArrayList<>();

        latest.run(pool, () -> "old", shown::add, ui);
        pool.runAll();
        latest.run(pool, () -> "new", shown::add, ui);
        pool.runAll();
        ui.runAll();

        assertThat(shown).containsExactly("new");
    }
}
