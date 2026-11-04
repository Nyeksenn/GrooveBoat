package io.github.Nyeksenn.grooveboat.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoverSizesTest {
    @Test
    void thumbnailsGetTheSmallestLevelAtOneTimesScale() {
        for (var size : new int[]{30, 40, 50, 64}) {
            assertThat(CoverSizes.bucketFor(size, 1)).isEqualTo(64);
        }
    }

    @Test
    void thumbnailsGetTheNextLevelAtTwoTimesScale() {
        for (var size : new int[]{30, 40, 50, 64}) {
            assertThat(CoverSizes.bucketFor(size, 2)).isEqualTo(size <= 32 ? 64 : 128);
        }
    }

    @Test
    void gridAndPlayerSizes() {
        assertThat(CoverSizes.bucketFor(200, 1)).isEqualTo(256);
        assertThat(CoverSizes.bucketFor(250, 1)).isEqualTo(256);
        assertThat(CoverSizes.bucketFor(200, 2)).isEqualTo(512);
        assertThat(CoverSizes.bucketFor(250, 2)).isEqualTo(512);
    }

    @Test
    void fractionalScalesRoundUp() {
        assertThat(CoverSizes.bucketFor(50, 1.25)).isEqualTo(64);
        assertThat(CoverSizes.bucketFor(50, 1.5)).isEqualTo(128);
        assertThat(CoverSizes.bucketFor(200, 1.25)).isEqualTo(256);
    }

    @Test
    void neverExceedsTheLargestLevelAndToleratesBadInput() {
        assertThat(CoverSizes.bucketFor(2000, 3)).isEqualTo(512);
        assertThat(CoverSizes.bucketFor(0, 0)).isEqualTo(64);
        assertThat(CoverSizes.bucketFor(-5, -1)).isEqualTo(64);
    }

    @Test
    void theChosenLevelIsNeverMoreThanTwiceTheNeededSize() {
        for (var size = 16; size <= 250; size++) {
            var needed = size * 2;
            var bucket = CoverSizes.bucketFor(size, 2);
            assertThat(bucket).isGreaterThanOrEqualTo(Math.min(needed, 512));
            if (needed > 64 && needed <= 512) {
                assertThat(bucket).isLessThan(needed * 2);
            }
        }
    }
}
