package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.common.crafting.trinity.profile.TrinityDataCoreCpuContribution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class TrinityCpuListGeometryTest {

    @Test
    void progressUsesAeFlooringAcrossTheFullRowWidth() {
        assertEquals(0, TrinityCpuListGeometry.progressWidth(0.0F));
        assertEquals(6, TrinityCpuListGeometry.progressWidth(0.1F));
        assertEquals(33, TrinityCpuListGeometry.progressWidth(0.5F));
        assertEquals(66, TrinityCpuListGeometry.progressWidth(1.0F));
        assertThrows(IllegalArgumentException.class, () -> TrinityCpuListGeometry.progressWidth(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> TrinityCpuListGeometry.progressWidth(-0.01F));
        assertThrows(IllegalArgumentException.class, () -> TrinityCpuListGeometry.progressWidth(1.01F));
    }

    @Test
    void scrollbarThumbRemainsVisibleAtMaximumCpuScale() {
        assertEquals(75.0F, TrinityCpuListGeometry.thumbPercent(75.0F, 54.0F, true));
        int maximumCpuCount = TrinityDataCoreCpuContribution.MAX_PARTITION_COUNT + 1;
        float maximumCpuNaturalPercent = TrinityCpuStatusList.VIEWPORT_HEIGHT * 100.0F /
                (maximumCpuCount * TrinityCpuStatusList.ROW_STRIDE);
        float sixPixelThumbPercent = 6.0F * 100.0F / 57.0F;
        assertEquals(
                sixPixelThumbPercent,
                TrinityCpuListGeometry.thumbPercent(maximumCpuNaturalPercent, 57.0F, true),
                0.0001F);
        assertEquals(
                sixPixelThumbPercent,
                TrinityCpuListGeometry.thumbPercent(100.0F, 57.0F, false),
                0.0001F);
        assertEquals(100.0F, TrinityCpuListGeometry.thumbPercent(1.0F, 4.0F, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> TrinityCpuListGeometry.thumbPercent(Float.NaN, 54.0F, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> TrinityCpuListGeometry.thumbPercent(50.0F, -1.0F, true));
    }
}
