package io.github.Nyeksenn.grooveboat.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.ebean.Database;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SessionSlotTest {
    private static Database db;

    private final SessionSlot slot = new SessionSlot();
    private final StubBackend backend = new StubBackend();

    @BeforeAll
    static void startDatabase() {
        // LibrarySync looks up the default database when it is created
        db = TestDatabase.start();
    }

    @AfterAll
    static void stopDatabase() {
        db.shutdown();
    }

    @Test
    void startsEmpty() {
        assertThat(slot.isActive()).isFalse();
        assertThat(slot.current()).isNull();
        assertThat(slot.backend()).isNull();
        assertThat(slot.library()).isNull();
    }

    @Test
    void startFillsBackendAndLibraryAsAPair() {
        slot.start(backend);

        assertThat(slot.isActive()).isTrue();
        assertThat(slot.backend()).isSameAs(backend);
        assertThat(slot.library()).isNotNull();
        assertThat(slot.library().backend()).isSameAs(backend);
        assertThat(slot.current().backend()).isSameAs(slot.backend());
        assertThat(slot.current().library()).isSameAs(slot.library());
        slot.end();
    }

    @Test
    void aRunningSessionIsNeverReplaced() {
        slot.start(backend);
        var library = slot.library();

        assertThatThrownBy(() -> slot.start(new StubBackend())).isInstanceOf(IllegalStateException.class);

        assertThat(slot.backend()).isSameAs(backend);
        assertThat(slot.library()).isSameAs(library);
        slot.end();
    }

    @Test
    void endClosesBackendAndLibraryAndEmptiesTheSlot() {
        slot.start(backend);
        var library = slot.library();

        slot.end();

        assertThat(slot.isActive()).isFalse();
        assertThat(slot.backend()).isNull();
        assertThat(backend.closed).isTrue();
        // a closed sync is cancelled: refreshing it must not touch the server
        library.refresh(false);
        assertThat(backend.albumCalls.get()).isZero();
        assertThat(library.cancelAndAwait(Duration.ofSeconds(1))).isTrue();
    }

    @Test
    void endIsSafeWithoutASessionAndWhenRepeated() {
        slot.end();
        slot.start(backend);
        slot.end();
        slot.end();
        assertThat(slot.isActive()).isFalse();
    }

    @Test
    void aNewSessionCanStartAfterTheOldOneEnded() {
        slot.start(backend);
        slot.end();
        var second = new StubBackend();

        slot.start(second);

        assertThat(slot.backend()).isSameAs(second);
        assertThat(backend.closed).isTrue();
        assertThat(second.closed).isFalse();
        slot.end();
    }
}
