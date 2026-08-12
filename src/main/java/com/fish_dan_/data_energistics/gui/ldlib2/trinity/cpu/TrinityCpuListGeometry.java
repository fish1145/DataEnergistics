package com.fish_dan_.data_energistics.gui.ldlib2.trinity.cpu;

/** Pixel geometry shared by the native Trinity CPU rows and their vertical scrollbar. */
final class TrinityCpuListGeometry {

    private static final int PROGRESS_WIDTH = TrinityCpuStatusList.ROW_WIDTH - 1;
    private static final float MIN_SCROLL_THUMB_HEIGHT = 6.0F;

    private TrinityCpuListGeometry() {}

    static int progressWidth(float progress) {
        if (!Float.isFinite(progress) || progress < 0.0F || progress > 1.0F) {
            throw new IllegalArgumentException("Trinity CPU progress must be between zero and one");
        }
        return (int) Math.floor(progress * PROGRESS_WIDTH);
    }

    static float thumbPercent(float naturalPercent, float trackHeight, boolean overflowing) {
        if (!Float.isFinite(naturalPercent) || naturalPercent < 0.0F || naturalPercent > 100.0F) {
            throw new IllegalArgumentException("Trinity CPU scrollbar percentage must be between zero and one hundred");
        }
        if (!Float.isFinite(trackHeight) || trackHeight < 0.0F) {
            throw new IllegalArgumentException("Trinity CPU scrollbar track height must not be negative");
        }
        if (trackHeight == 0.0F) {
            return naturalPercent;
        }
        float minimumPercent = Math.min(100.0F, MIN_SCROLL_THUMB_HEIGHT * 100.0F / trackHeight);
        return overflowing ? Math.max(naturalPercent, minimumPercent) : minimumPercent;
    }
}
