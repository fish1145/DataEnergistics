package com.fish_dan_.data_energistics.orbital.control.ui.layout;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.hud.orbital.layout.OrbitalHudPlacement;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class OrbitalTerminalLayoutGameTest {

    private OrbitalTerminalLayoutGameTest() {}

    @TestHolder("orbital_terminal_layout_adapts_to_gui_scale")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void keepsPanelsInsideViewportAcrossLayoutBreakpoint(GameTestHelper helper) {
        for (int viewportWidth : new int[] { 320, 480, 675, 676, 677, 960, 1920 }) {
            var layout = OrbitalTerminalLayout.forViewport(viewportWidth, 240);
            helper.assertTrue(layout.width() < viewportWidth && layout.height() < 240,
                    "The terminal must fit within the GUI-scaled viewport");
            helper.assertTrue(layout.mapLeft() >= 0 && layout.mapLeft() + layout.mapWidth() <= layout.width(),
                    "Map bounds must remain inside the shell");
            helper.assertTrue(layout.formLeft() >= 0 && layout.formLeft() + layout.formWidth() <= layout.width(),
                    "The form must remain inside the shell");
            helper.assertTrue(layout.bodyHeight() > 0 && layout.bodyTop() + layout.bodyHeight() <= layout.height() - 48,
                    "Scrollable content must not cover the fixed footer");
            helper.assertValueEqual(layout.wide(), viewportWidth >= 676,
                    "The layout must change exactly at its readable three-panel breakpoint");
        }
        helper.succeed();
    }

    @TestHolder("orbital_hud_placement_clamps_resize_and_growing_content")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void keepsAnchoredHudVisibleAfterResizeAndTaskChanges(GameTestHelper helper) {
        var idle = OrbitalHudPlacement.resolve(800, 450, 210, 32, 1, true, true, 8, 8);
        var active = OrbitalHudPlacement.resolve(800, 450, 210, 68, 1, true, true, 8, 8);
        helper.assertValueEqual(idle.y() + idle.height(), active.y() + active.height(),
                "Adding tasks must keep the bottom anchor stable");
        for (boolean right : new boolean[] { false, true }) {
            for (boolean bottom : new boolean[] { false, true }) {
                var small = OrbitalHudPlacement.resolve(320, 240, 210, 68, 1.75F, right, bottom, 4096, 4096);
                helper.assertTrue(small.x() >= 0 && small.x() + small.width() <= 320 &&
                        small.y() >= 0 && small.y() + small.height() <= 240,
                        "Saved offsets and oversized scaling must fit a smaller GUI viewport");
            }
        }
        helper.succeed();
    }
}
