package com.fish_dan_.data_energistics.gui.ldlib2;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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

    @Test
    void cascadesFourFreshLargeWindowsAtTheMinimumGuiSize() {
        List<HostWindowPlacement.ScreenPosition> positions = new ArrayList<>();
        List<HostWindowPlacement.ScreenBounds> bounds = new ArrayList<>();

        for (int index = 0; index < 4; index++) {
            HostWindowPlacement.ScreenPosition position = HostWindowPlacement.cascadeInitial(
                    320,
                    240,
                    4,
                    4,
                    292,
                    210,
                    List.copyOf(bounds));
            positions.add(position);
            bounds.add(new HostWindowPlacement.ScreenBounds(position.left(), position.top(), 292, 210));
        }

        assertEquals(List.of(
                new HostWindowPlacement.ScreenPosition(4, 4),
                new HostWindowPlacement.ScreenPosition(11, 11),
                new HostWindowPlacement.ScreenPosition(18, 18),
                new HostWindowPlacement.ScreenPosition(24, 25)), positions);
        assertEquals(
                new HostWindowPlacement.ScreenPosition(150, 4),
                HostWindowPlacement.cascadeInitial(
                        320,
                        240,
                        150,
                        4,
                        100,
                        100,
                        List.of(new HostWindowPlacement.ScreenBounds(4, 4, 100, 100))));
    }
}
