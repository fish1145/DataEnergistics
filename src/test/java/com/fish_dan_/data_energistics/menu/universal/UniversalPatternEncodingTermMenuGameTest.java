package com.fish_dan_.data_energistics.menu.universal;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;
import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.util.PatternEncodingPreviewLayoutHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.parts.IPart;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class UniversalPatternEncodingTermMenuGameTest {

    private static final BlockPos TERMINAL_HOST_POS = new BlockPos(1, 1, 1);

    private UniversalPatternEncodingTermMenuGameTest() {}

    @TestHolder("universal_pattern_encoding_menu_uses_inherited_preview_actions")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void usesInheritedPreviewActions(GameTestHelper helper) {
        CableBusBlockEntity partHost = placeCableBus(helper);
        IPart installedPart = partHost.addPart(DEItems.UNIVERSAL_TERMINAL.get(), Direction.NORTH, null);
        if (!(installedPart instanceof UniversalTerminalPart terminal)) {
            throw new GameTestAssertException("Failed to install a real universal terminal part");
        }

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        UniversalPatternEncodingTermMenu menu = new UniversalPatternEncodingTermMenu(
                1,
                player.getInventory(),
                terminal);

        menu.receiveClientAction(
                PatternEncodingPreviewLayoutHelper.ACTION_SET_PREVIEW_PANEL_OFFSET,
                "\"17,-8\"");
        assertEquals(17, menu.previewPanelOffsetX);
        assertEquals(-8, menu.previewPanelOffsetY);
        assertEquals(17, terminal.getPersistentPreviewPanelOffsetX());
        assertEquals(-8, terminal.getPersistentPreviewPanelOffsetY());

        menu.receiveClientAction(
                PatternEncodingPreviewLayoutHelper.ACTION_RESET_PREVIEW_PANEL_OFFSET,
                "null");
        assertEquals(0, menu.previewPanelOffsetX);
        assertEquals(0, menu.previewPanelOffsetY);
        assertEquals(0, terminal.getPersistentPreviewPanelOffsetX());
        assertEquals(0, terminal.getPersistentPreviewPanelOffsetY());
        helper.succeed();
    }

    private static CableBusBlockEntity placeCableBus(GameTestHelper helper) {
        helper.setBlock(TERMINAL_HOST_POS, AEBlocks.CABLE_BUS.block().defaultBlockState());
        BlockEntity blockEntity = helper.getBlockEntity(TERMINAL_HOST_POS);
        if (blockEntity instanceof CableBusBlockEntity cableBus) {
            return cableBus;
        }
        throw new GameTestAssertException("Placed AE cable bus has no matching block entity");
    }

    private static void assertEquals(int expected, int actual) {
        if (expected != actual) {
            throw new GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }
}
