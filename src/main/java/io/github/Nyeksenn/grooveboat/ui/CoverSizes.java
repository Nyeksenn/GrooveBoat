package io.github.Nyeksenn.grooveboat.ui;

/**
 * The size levels ("mip levels") covers are decoded at. A widget gets the smallest level that still covers the
 * physical pixels it draws, so the GPU only ever downsamples by at most 2x (plain bilinear filtering suffices) and no
 * widget holds a full-size texture for a thumbnail.
 */
public final class CoverSizes {
    /** Edge lengths in pixels, ascending. The largest covers the biggest widget (250 logical px) at 2x scale. */
    public static final int[] BUCKETS = {64, 128, 256, 512};

    private CoverSizes() {
    }

    /** The smallest level of at least {@code logicalSize * scale} pixels, capped at the largest level. */
    public static int bucketFor(int logicalSize, double scale) {
        var needed = Math.ceil(Math.max(logicalSize, 1) * Math.max(scale, 1.0));
        for (var bucket : BUCKETS) {
            if (bucket >= needed) {
                return bucket;
            }
        }
        return BUCKETS[BUCKETS.length - 1];
    }
}
