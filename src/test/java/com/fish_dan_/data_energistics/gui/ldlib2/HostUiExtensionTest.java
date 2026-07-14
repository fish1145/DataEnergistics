package com.fish_dan_.data_energistics.gui.ldlib2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class HostUiExtensionTest {

    private static final float POSITION_TOLERANCE = 0.001F;

    @Test
    void translatesClampedScreenCoordinatesBackToParentLayout() {
        HostWindowPlacement placement = HostWindowPlacement.clamp(200, 120, 190, 120, 140, 100, 40, 30);

        assertEquals(192, placement.maximumWidth());
        assertEquals(112, placement.maximumHeight());
        assertEquals(106, placement.left(), POSITION_TOLERANCE);
        assertEquals(66, placement.top(), POSITION_TOLERANCE);

        HostWindowPlacement retained = HostWindowPlacement.clamp(200, 120, 156, 86, 106, 66, 40, 30);
        assertEquals(106, retained.left(), POSITION_TOLERANCE);
        assertEquals(66, retained.top(), POSITION_TOLERANCE);
    }
}
