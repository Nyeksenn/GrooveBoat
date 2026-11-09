package io.github.Nyeksenn.grooveboat.ui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/** A thread-safe least-recently-used map bounded by the summed weight of its values instead of their count. */
final class WeightedLru<K, V> {
    private final long maxWeight;
    private final ToLongFunction<V> weigher;
    private final LinkedHashMap<K, V> map = new LinkedHashMap<>(64, 0.75f, true);
    private long weight;

    WeightedLru(long maxWeight, ToLongFunction<V> weigher) {
        this.maxWeight = maxWeight;
        this.weigher = weigher;
    }

    synchronized V get(K key) {
        return map.get(key);
    }

    synchronized void put(K key, V value) {
        var old = map.put(key, value);
        if (old != null) {
            weight -= weigher.applyAsLong(old);
        }
        weight += weigher.applyAsLong(value);
        var it = map.entrySet().iterator();
        // Evict the least recently used entries, but never the one just added.
        while (weight > maxWeight && it.hasNext()) {
            Map.Entry<K, V> eldest = it.next();
            if (eldest.getKey().equals(key)) {
                continue;
            }
            weight -= weigher.applyAsLong(eldest.getValue());
            it.remove();
        }
    }

    synchronized void removeIf(Predicate<K> keys) {
        var it = map.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (keys.test(entry.getKey())) {
                weight -= weigher.applyAsLong(entry.getValue());
                it.remove();
            }
        }
    }

    synchronized void clear() {
        map.clear();
        weight = 0;
    }

    synchronized long weight() {
        return weight;
    }

    synchronized int size() {
        return map.size();
    }
}
