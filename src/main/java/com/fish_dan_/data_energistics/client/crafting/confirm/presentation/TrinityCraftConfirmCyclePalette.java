package com.fish_dan_.data_energistics.client.crafting.confirm.presentation;

import net.minecraft.util.Mth;

/**
 * Assigns a stable, high-contrast color to each one-based cycle ordinal.
 */
public final class TrinityCraftConfirmCyclePalette {

    private static final int[] ACCESSIBLE_COLORS = {
            0xFF4FA3E3,
            0xFFE69F2D,
            0xFF2CB67D,
            0xFFE45756,
            0xFFB279D6,
            0xFFD9B72F,
            0xFF3F7FC4,
            0xFFD67AB1
    };
    private static final double GOLDEN_ANGLE_TURN = 0.3819660112501051D;
    private static final double EXTRA_HUE_ORIGIN = 0.08D;

    private TrinityCraftConfirmCyclePalette() {}

    /**
     * Returns the opaque ARGB color assigned to a displayed cycle.
     *
     * @param displayOrdinal validated one-based cycle ordinal
     * @return deterministic opaque color
     */
    public static int argb(int displayOrdinal) {
        if (displayOrdinal <= 0) {
            throw new IllegalArgumentException("A cycle display ordinal must be positive");
        }
        int colorIndex = displayOrdinal - 1;
        if (colorIndex < ACCESSIBLE_COLORS.length) {
            return ACCESSIBLE_COLORS[colorIndex];
        }

        int generatedIndex = colorIndex - ACCESSIBLE_COLORS.length;
        double hue = (EXTRA_HUE_ORIGIN + generatedIndex * GOLDEN_ANGLE_TURN) % 1.0D;
        return 0xFF000000 | Mth.hsvToRgb((float) hue, 0.68F, 0.92F);
    }

    /**
     * Returns the RGB portion used to color the textual cycle title.
     *
     * @param displayOrdinal validated one-based cycle ordinal
     * @return deterministic RGB color
     */
    public static int rgb(int displayOrdinal) {
        return argb(displayOrdinal) & 0x00FFFFFF;
    }
}
