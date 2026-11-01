package io.github.Nyeksenn.grooveboat.audio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Random;

/**
 * The pure queue logic behind {@link QueueStore}: the (optionally shuffled) play order and which entry comes before or
 * after the current one. Entries are compared by identity, since equal-looking queue entries can be distinct songs.
 * Not thread-safe; used on the GTK main thread only.
 */
final class PlayOrder<T> {
    private final List<T> shuffled = new ArrayList<>();
    private final Random random;

    PlayOrder(Random random) {
        this.random = random;
    }

    /** Forgets the shuffled order, e.g. when shuffle is switched on or off. */
    void reset() {
        shuffled.clear();
    }

    /**
     * Queue entries in play order: {@code queue} itself, or the shuffled order while {@code shuffle} is on. The
     * shuffled order is first brought in line with the queue: entries that left are dropped, new entries are inserted
     * at random places after {@code current}.
     */
    List<T> resolve(List<T> queue, T current, boolean shuffle) {
        if (!shuffle) {
            return queue;
        }
        reconcile(queue, current);
        return shuffled;
    }

    private void reconcile(List<T> queue, T current) {
        var present = Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
        present.addAll(queue);
        shuffled.removeIf(item -> !present.contains(item));
        int n = queue.size();
        if (shuffled.size() == n) {
            return;
        }
        if (current != null && shuffled.isEmpty() && present.contains(current)) {
            shuffled.add(current);
        }
        var inOrder = Collections.newSetFromMap(new IdentityHashMap<T, Boolean>());
        inOrder.addAll(shuffled);
        int curPos = current == null ? -1 : identityIndex(shuffled, current);
        for (var item : queue) {
            if (inOrder.add(item)) {
                shuffled.add(curPos + 1 + random.nextInt(shuffled.size() - curPos), item);
            }
        }
    }

    /** The entry a skip-forward would play (wrapping in repeat-playlist mode), or {@code null}. */
    static <T> T after(List<T> order, T current, RepeatMode repeat) {
        if (current == null) {
            return null;
        }
        int i = identityIndex(order, current);
        if (i >= 0 && i + 1 < order.size()) {
            return order.get(i + 1);
        }
        return repeat == RepeatMode.PLAYLIST && !order.isEmpty() ? order.getFirst() : null;
    }

    /** The entry a skip-back would play (wrapping in repeat-playlist mode), or {@code null}. */
    static <T> T before(List<T> order, T current, RepeatMode repeat) {
        if (current == null) {
            return null;
        }
        int i = identityIndex(order, current);
        if (i > 0) {
            return order.get(i - 1);
        }
        return repeat == RepeatMode.PLAYLIST && !order.isEmpty() ? order.getLast() : null;
    }

    private static <T> int identityIndex(List<T> list, T item) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == item) {
                return i;
            }
        }
        return -1;
    }
}
