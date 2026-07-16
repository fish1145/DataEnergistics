package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.DataRipperReassemblerBlock;
import com.fish_dan_.data_energistics.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.util.AECableType;

import java.util.Set;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataRipperReassemblerOrientationGameTest {

    private static final BlockPos REASSEMBLER_POS = new BlockPos(2, 2, 2);

    private DataRipperReassemblerOrientationGameTest() {}

    @TestHolder("data_reassembler_tracks_horizontal_orientation")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void tracksHorizontalOrientation(GameTestHelper helper) {
        helper.setBlock(REASSEMBLER_POS, ModBlocks.DATA_RIPPER_REASSEMBLER.get()
                .defaultBlockState()
                .setValue(DataRipperReassemblerBlock.FACING, Direction.NORTH));
        DataRipperReassemblerBlockEntity reassembler = requireReassembler(helper);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        reassembler.getMainNode().getNode() != null,
                        "The Data Reassembler managed node did not become ready"))
                .thenExecute(() -> {
                    for (Direction facing : Direction.Plane.HORIZONTAL) {
                        helper.setBlock(
                                REASSEMBLER_POS,
                                reassembler.getBlockState().setValue(DataRipperReassemblerBlock.FACING, facing));
                        helper.assertTrue(
                                helper.getBlockEntity(REASSEMBLER_POS) == reassembler,
                                "Changing the horizontal facing must retain the existing block entity");
                        assertOrientation(helper, reassembler, facing);
                    }
                })
                .thenSucceed();
    }

    private static DataRipperReassemblerBlockEntity requireReassembler(GameTestHelper helper) {
        BlockEntity blockEntity = helper.getBlockEntity(REASSEMBLER_POS);
        if (blockEntity instanceof DataRipperReassemblerBlockEntity reassembler) {
            return reassembler;
        }
        throw new GameTestAssertException("Placed Data Reassembler has no matching block entity");
    }

    private static void assertOrientation(
                                          GameTestHelper helper,
                                          DataRipperReassemblerBlockEntity reassembler,
                                          Direction facing) {
        BlockOrientation orientation = reassembler.getOrientation();
        helper.assertValueEqual(
                orientation.getSide(RelativeSide.FRONT),
                facing,
                "The AE2 front must follow the horizontal block facing");
        helper.assertValueEqual(
                orientation.getSide(RelativeSide.TOP),
                Direction.UP,
                "The AE2 top must remain upward for every horizontal facing");

        Set<Direction> exposedSides = reassembler.getGridConnectableSides(orientation);
        for (Direction side : Direction.values()) {
            boolean expectedExposed = side != facing && side != Direction.UP;
            helper.assertValueEqual(
                    exposedSides.contains(side),
                    expectedExposed,
                    "The declared cable exposure must exclude only the front and top: " + side);
            helper.assertValueEqual(
                    reassembler.getCableConnectionType(side),
                    expectedExposed ? AECableType.COVERED : AECableType.NONE,
                    "The cable connection type must follow the declared exposure: " + side);
            helper.assertValueEqual(
                    reassembler.getGridNode(side) != null,
                    expectedExposed,
                    "The live managed node exposure must follow the rotated block: " + side);
        }
    }
}
