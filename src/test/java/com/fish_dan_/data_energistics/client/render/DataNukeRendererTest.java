package com.fish_dan_.data_energistics.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class DataNukeRendererTest {

    private static final float DELTA = 0.00001F;

    @Test
    void inactiveRayProgressTracksTheFullFuse() {
        assertEquals(0.0F, DataNukeRenderer.calculateRayProgress(false, 80, 0.0F), DELTA);
        assertEquals(0.5F, DataNukeRenderer.calculateRayProgress(false, 40, 0.0F), DELTA);
        assertEquals(0.99875F, DataNukeRenderer.calculateRayProgress(false, 1, 0.9F), DELTA);
    }

    @Test
    void shortenedFuseStartsAtItsCorrespondingProgress() {
        assertEquals(0.75F, DataNukeRenderer.calculateRayProgress(false, 20, 0.0F), DELTA);
    }

    @Test
    void activeRayProgressStaysFullyExpandedAfterFuseReset() {
        assertEquals(1.0F, DataNukeRenderer.calculateRayProgress(true, 80, 0.0F), DELTA);
    }

    @Test
    void inactiveRayProgressIsClampedToValidBounds() {
        assertEquals(0.0F, DataNukeRenderer.calculateRayProgress(false, 100, 0.0F), DELTA);
        assertEquals(1.0F, DataNukeRenderer.calculateRayProgress(false, -20, 0.0F), DELTA);
    }
}
