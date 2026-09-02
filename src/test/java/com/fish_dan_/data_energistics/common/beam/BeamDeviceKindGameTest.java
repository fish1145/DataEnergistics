package com.fish_dan_.data_energistics.common.beam;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class BeamDeviceKindGameTest {

    private BeamDeviceKindGameTest() {}

    @TestHolder("beam_card_range_adds_eight_blocks_per_card")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void cardRangeAddsEightBlocksPerCard(GameTestHelper helper) {
        assertRanges(BeamDeviceKind.PART, 64, 72, 80, 88);
        assertRanges(BeamDeviceKind.DIRECTIONAL, 64, 72, 80, 88);
        assertRanges(BeamDeviceKind.OMNI, 128, 136, 144, 152);
        helper.succeed();
    }

    private static void assertRanges(BeamDeviceKind kind, int zeroCards, int oneCard, int twoCards, int threeCards) {
        assertEquals(zeroCards, kind.range(0));
        assertEquals(oneCard, kind.range(1));
        assertEquals(twoCards, kind.range(2));
        assertEquals(threeCards, kind.range(3));
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }
}
