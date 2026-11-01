package io.github.Nyeksenn.grooveboat.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PlayOrderTest {
    private record Song(String name) {
    }

    private final Song a = new Song("a");
    private final Song b = new Song("b");
    private final Song c = new Song("c");
    private final List<Song> queue = List.of(a, b, c);

    @Test
    void afterWalksTheQueueAndStopsAtTheEnd() {
        assertThat(PlayOrder.after(queue, a, RepeatMode.NONE)).isSameAs(b);
        assertThat(PlayOrder.after(queue, b, RepeatMode.NONE)).isSameAs(c);
        assertThat(PlayOrder.after(queue, c, RepeatMode.NONE)).isNull();
        assertThat(PlayOrder.after(queue, c, RepeatMode.TRACK)).isNull();
    }

    @Test
    void afterWrapsInPlaylistRepeat() {
        assertThat(PlayOrder.after(queue, c, RepeatMode.PLAYLIST)).isSameAs(a);
    }

    @Test
    void beforeWalksBackAndWraps() {
        assertThat(PlayOrder.before(queue, c, RepeatMode.NONE)).isSameAs(b);
        assertThat(PlayOrder.before(queue, a, RepeatMode.NONE)).isNull();
        assertThat(PlayOrder.before(queue, a, RepeatMode.PLAYLIST)).isSameAs(c);
    }

    @Test
    void entriesAreComparedByIdentityNotValue() {
        var twin1 = new Song("same");
        var twin2 = new Song("same");
        var twins = List.of(twin1, twin2);
        assertThat(PlayOrder.after(twins, twin1, RepeatMode.NONE)).isSameAs(twin2);
        assertThat(PlayOrder.after(twins, twin2, RepeatMode.NONE)).isNull();
        assertThat(PlayOrder.before(twins, twin2, RepeatMode.NONE)).isSameAs(twin1);
    }

    @Test
    void noCurrentMeansNoNeighbour() {
        assertThat(PlayOrder.after(queue, null, RepeatMode.PLAYLIST)).isNull();
        assertThat(PlayOrder.before(queue, null, RepeatMode.PLAYLIST)).isNull();
    }

    @Test
    void emptyQueueNeverWraps() {
        List<Song> empty = List.of();
        assertThat(PlayOrder.after(empty, a, RepeatMode.PLAYLIST)).isNull();
        assertThat(PlayOrder.before(empty, a, RepeatMode.PLAYLIST)).isNull();
    }

    @Test
    void unshuffledOrderIsTheQueue() {
        var order = new PlayOrder<Song>(new Random(1));
        assertThat(order.resolve(queue, a, false)).isSameAs(queue);
    }

    @Test
    void shuffledOrderContainsEveryEntryOnceWithCurrentFirst() {
        var order = new PlayOrder<Song>(new Random(42));
        var resolved = order.resolve(queue, b, true);
        assertThat(resolved).hasSize(3).containsExactlyInAnyOrder(a, b, c);
        assertThat(resolved.getFirst()).isSameAs(b);
    }

    @Test
    void shuffledOrderIsStableBetweenCalls() {
        var order = new PlayOrder<Song>(new Random(7));
        var first = List.copyOf(order.resolve(queue, a, true));
        assertThat(order.resolve(queue, a, true)).containsExactlyElementsOf(first);
    }

    @Test
    void shuffledOrderDropsRemovedEntriesAndInsertsNewOnesAfterCurrent() {
        var order = new PlayOrder<Song>(new Random(3));
        order.resolve(queue, a, true);

        var added = new Song("added");
        var changed = new ArrayList<>(List.of(a, c, added));
        var resolved = order.resolve(changed, a, true);

        assertThat(resolved).hasSize(3).containsExactlyInAnyOrder(a, c, added);
        assertThat(resolved.indexOf(added)).isGreaterThan(resolved.indexOf(a));
    }

    @Test
    void resetRebuildsTheShuffleFromTheCurrentEntry() {
        var order = new PlayOrder<Song>(new Random(5));
        order.resolve(queue, a, true);
        order.reset();
        assertThat(order.resolve(queue, c, true).getFirst()).isSameAs(c);
    }
}
