package com.fish_dan_.data_energistics.block.machine;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

@PrefixGameTestTemplate(false)
@GameTestHolder(Data_Energistics.MODID)
public final class DataSolarPanelBlockConnectionGameTest {

    private static final BlockPos SOLAR_PANEL_POS = new BlockPos(2, 2, 2);
    private static final BlockPos EAST_SOLAR_PANEL_POS = SOLAR_PANEL_POS.relative(Direction.EAST);
    private static final BlockPos NORTH_SOLAR_PANEL_POS = SOLAR_PANEL_POS.relative(Direction.NORTH);
    private static final BlockPos DATA_SOLAR_PANEL_POS = new BlockPos(2, 2, 4);
    private static final BlockPos EAST_DATA_SOLAR_PANEL_POS = DATA_SOLAR_PANEL_POS.relative(Direction.EAST);

    private DataSolarPanelBlockConnectionGameTest() {}

    @TestHolder("me_solar_panels_connect_only_to_matching_panel_type")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void panelsConnectOnlyToMatchingPanelType(GameTestHelper helper) {
        helper.setBlock(SOLAR_PANEL_POS, DEBlocks.DATA_SOLAR_PANEL.get());
        helper.setBlock(EAST_SOLAR_PANEL_POS, DEBlocks.DATA_SOLAR_PANEL.get());

        assertConnection(helper, SOLAR_PANEL_POS, DataSolarPanelBlock.CONNECT_EAST, true);
        assertConnection(helper, EAST_SOLAR_PANEL_POS, DataSolarPanelBlock.CONNECT_WEST, true);

        helper.setBlock(NORTH_SOLAR_PANEL_POS, DEBlocks.ME_DATA_SOLAR_PANEL.get());
        assertConnection(helper, SOLAR_PANEL_POS, DataSolarPanelBlock.CONNECT_NORTH, false);
        assertConnection(helper, NORTH_SOLAR_PANEL_POS, DataSolarPanelBlock.CONNECT_SOUTH, false);

        helper.setBlock(EAST_SOLAR_PANEL_POS, Blocks.AIR);
        assertConnection(helper, SOLAR_PANEL_POS, DataSolarPanelBlock.CONNECT_EAST, false);

        helper.setBlock(DATA_SOLAR_PANEL_POS, DEBlocks.ME_DATA_SOLAR_PANEL.get());
        helper.setBlock(EAST_DATA_SOLAR_PANEL_POS, DEBlocks.ME_DATA_SOLAR_PANEL.get());
        assertConnection(helper, DATA_SOLAR_PANEL_POS, DataSolarPanelBlock.CONNECT_EAST, true);
        assertConnection(helper, EAST_DATA_SOLAR_PANEL_POS, DataSolarPanelBlock.CONNECT_WEST, true);
        helper.succeed();
    }

    @TestHolder("me_solar_panel_connections_follow_structure_rotation_and_mirroring")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5")
    public static void connectionsFollowStructureRotationAndMirroring(GameTestHelper helper) {
        BlockState state = DEBlocks.DATA_SOLAR_PANEL.get().defaultBlockState()
                .setValue(DataSolarPanelBlock.FACING, Direction.NORTH)
                .setValue(DataSolarPanelBlock.CONNECT_NORTH, true)
                .setValue(DataSolarPanelBlock.CONNECT_EAST, true);

        BlockState rotated = state.rotate(Rotation.CLOCKWISE_90);
        helper.assertValueEqual(rotated.getValue(DataSolarPanelBlock.FACING), Direction.EAST,
                "Clockwise rotation must rotate the panel facing");
        helper.assertTrue(rotated.getValue(DataSolarPanelBlock.CONNECT_EAST),
                "Clockwise rotation must move the north connection east");
        helper.assertTrue(rotated.getValue(DataSolarPanelBlock.CONNECT_SOUTH),
                "Clockwise rotation must move the east connection south");

        BlockState mirrored = state.mirror(Mirror.FRONT_BACK);
        helper.assertValueEqual(mirrored.getValue(DataSolarPanelBlock.FACING), Direction.NORTH,
                "Front-back mirroring must preserve a north-facing panel");
        helper.assertTrue(mirrored.getValue(DataSolarPanelBlock.CONNECT_NORTH),
                "Front-back mirroring must preserve the north connection");
        helper.assertTrue(mirrored.getValue(DataSolarPanelBlock.CONNECT_WEST),
                "Front-back mirroring must move the east connection west");
        helper.succeed();
    }

    private static void assertConnection(GameTestHelper helper, BlockPos pos, BooleanProperty direction, boolean expected) {
        BlockState state = helper.getBlockState(pos);
        helper.assertValueEqual(
                state.getValue(direction),
                expected,
                "Unexpected connection state at " + pos + " for " + direction.getName());
    }
}
