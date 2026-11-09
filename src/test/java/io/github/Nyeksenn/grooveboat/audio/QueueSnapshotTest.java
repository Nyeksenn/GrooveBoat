package io.github.Nyeksenn.grooveboat.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QueueSnapshotTest {
    @Test
    void roundTripsThroughAFileAndKeepsDuplicates(@TempDir Path dir) throws IOException {
        var file = dir.resolve("sub/queue.json");
        QueueSnapshot.write(file, new QueueSnapshot(List.of("a", "b", "a", "c"), 2));

        var read = QueueSnapshot.read(file);

        assertThat(read).contains(new QueueSnapshot(List.of("a", "b", "a", "c"), 2));
        try (var files = Files.list(file.getParent())) {
            assertThat(files).containsExactly(file);
        }
    }

    @Test
    void writingAnEmptyQueueRemovesTheFile(@TempDir Path dir) throws IOException {
        var file = dir.resolve("queue.json");
        QueueSnapshot.write(file, new QueueSnapshot(List.of("a"), 0));
        QueueSnapshot.write(file, new QueueSnapshot(List.of(), 0));

        assertThat(file).doesNotExist();
        assertThat(QueueSnapshot.read(file)).isEmpty();
    }

    @Test
    void missingOrBrokenFilesReadAsNoQueue(@TempDir Path dir) throws IOException {
        var file = dir.resolve("queue.json");
        assertThat(QueueSnapshot.read(file)).isEmpty();

        Files.writeString(file, "{not json");
        assertThat(QueueSnapshot.read(file)).isEmpty();
        Files.writeString(file, "{\"songIds\":null,\"index\":1}");
        assertThat(QueueSnapshot.read(file)).isEmpty();
        Files.writeString(file, "{\"songIds\":[],\"index\":0}");
        assertThat(QueueSnapshot.read(file)).isEmpty();
    }

    @Test
    void anOutOfRangeIndexIsClamped() {
        assertThat(new QueueSnapshot(List.of("a", "b"), 9).index()).isEqualTo(1);
        assertThat(new QueueSnapshot(List.of("a", "b"), -3).index()).isZero();
    }

    @Test
    void retainKeepsTheCurrentSongCurrent() {
        var snapshot = new QueueSnapshot(List.of("a", "b", "c", "d"), 2).retain(Set.of("a", "c", "d"));

        assertThat(snapshot.songIds()).containsExactly("a", "c", "d");
        assertThat(snapshot.index()).isEqualTo(1);
    }

    @Test
    void whenTheCurrentSongIsGoneTheNextOneTakesOver() {
        var snapshot = new QueueSnapshot(List.of("a", "b", "c", "d"), 1).retain(Set.of("a", "c", "d"));

        assertThat(snapshot.songIds()).containsExactly("a", "c", "d");
        assertThat(snapshot.songIds().get(snapshot.index())).isEqualTo("c");
    }

    @Test
    void whenTheCurrentSongIsGoneAtTheEndTheLastRemainingOneIsCurrent() {
        var snapshot = new QueueSnapshot(List.of("a", "b", "c"), 2).retain(Set.of("a", "b"));

        assertThat(snapshot.songIds().get(snapshot.index())).isEqualTo("b");
    }

    @Test
    void whenNothingBeforeTheGoneCurrentSongRemainsTheFirstRemainingIsCurrent() {
        var snapshot = new QueueSnapshot(List.of("a", "b", "c"), 1).retain(Set.of("c"));

        assertThat(snapshot.songIds()).containsExactly("c");
        assertThat(snapshot.index()).isZero();
    }

    @Test
    void retainingFromAnEmptyQueueIsHarmless() {
        assertThat(new QueueSnapshot(List.of(), 0).retain(Set.of("a")).songIds()).isEmpty();
    }

    @Test
    void retainingNothingGivesAnEmptyQueue() {
        assertThat(new QueueSnapshot(List.of("a", "b"), 1).retain(Set.of()).songIds()).isEmpty();
    }
}
