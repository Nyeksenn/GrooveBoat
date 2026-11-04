package io.github.Nyeksenn.grooveboat.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeightedLruTest {
    private final WeightedLru<String, String> lru = new WeightedLru<>(10, String::length);

    @Test
    void evictsTheLeastRecentlyUsedEntriesOnceOverTheWeightLimit() {
        lru.put("a", "1234");
        lru.put("b", "1234");
        lru.get("a");
        lru.put("c", "1234");

        assertThat(lru.get("b")).isNull();
        assertThat(lru.get("a")).isEqualTo("1234");
        assertThat(lru.get("c")).isEqualTo("1234");
        assertThat(lru.weight()).isEqualTo(8);
    }

    @Test
    void keepsTheNewestEntryEvenWhenItAloneIsTooHeavy() {
        lru.put("a", "12");
        lru.put("big", "123456789012");

        assertThat(lru.get("big")).isNotNull();
        assertThat(lru.get("a")).isNull();
    }

    @Test
    void replacingAValueCorrectsTheWeight() {
        lru.put("a", "12345");
        lru.put("a", "12");

        assertThat(lru.weight()).isEqualTo(2);
        assertThat(lru.size()).isEqualTo(1);
    }

    @Test
    void removeIfAndClearReleaseWeight() {
        lru.put("a:1", "123");
        lru.put("b:1", "123");
        lru.removeIf(key -> key.startsWith("a"));

        assertThat(lru.weight()).isEqualTo(3);
        lru.clear();
        assertThat(lru.weight()).isZero();
        assertThat(lru.size()).isZero();
    }
}
