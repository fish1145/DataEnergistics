package com.fish_dan_.data_energistics.blockentity.beam;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.beam.BeamFormerBlock;
import com.fish_dan_.data_energistics.registry.DEBlocks;
import com.fish_dan_.data_energistics.registry.DEItems;

import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class BeamFormerRemovalGameTest {

    private BeamFormerRemovalGameTest() {}

    @TestHolder("online_beam_wrench_removal_keeps_air_and_returns_each_item_once")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 160)
    public static void onlineWrenchRemovalKeepsAirAndReturnsEachItemOnce(GameTestHelper helper) {
        // Both canonical connection roles are exercised, including the remotely powered receiver.
        List<Pair> pairs = List.of(placePair(helper, 1, true), placePair(helper, 3, false));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (Pair pair : pairs) {
                        helper.assertTrue(pair.removed().getMainNode().isPowered() && pair.peer().getMainNode().isPowered(),
                                "Both beam endpoints must be powered before wrench removal");
                        helper.assertValueEqual(pair.removed().beamState().connectionCount(), 1, "Fixture must establish a live beam");
                        helper.assertValueEqual(pair.peer().beamState().connectionCount(), 1, "Peer must share the live connection");
                        helper.assertValueEqual(pair.removed().getBlockState().getValue(BeamFormerBlock.STATUS), BeamFormerBlock.Status.BEAMING,
                                "Removal must exercise the connected block-state transition");
                    }
                })
                .thenExecute(() -> {
                    for (Pair pair : pairs) {
                        BlockPos position = pair.removed().getBlockPos();
                        pair.removed().disassembleWithWrench(pair.player(), helper.getLevel(),
                                new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false), AEItems.CERTUS_QUARTZ_WRENCH.stack());
                        assertRemoved(helper, pair);
                        pair.removed().setRemoved();
                    }
                })
                .thenExecuteAfter(25, () -> pairs.forEach(pair -> assertRemoved(helper, pair)))
                .thenSucceed();
    }

    private static Pair placePair(GameTestHelper helper, int z, boolean removeReceiver) {
        BlockPos senderPosition = new BlockPos(1, 1, z);
        BlockPos receiverPosition = new BlockPos(3, 1, z);
        helper.setBlock(new BlockPos(0, 1, z), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(senderPosition, DEBlocks.ME_BEAM_FORMER.get().defaultBlockState().setValue(BeamFormerBlock.FACING, Direction.EAST));
        helper.setBlock(receiverPosition, DEBlocks.ME_BEAM_FORMER.get().defaultBlockState().setValue(BeamFormerBlock.FACING, Direction.WEST));
        BeamFormerBlockEntity sender = helper.getBlockEntity(senderPosition);
        BeamFormerBlockEntity receiver = helper.getBlockEntity(receiverPosition);
        BeamFormerBlockEntity removed = removeReceiver ? receiver : sender;
        removed.getUpgrades().setItemDirect(0, new ItemStack(DEItems.CARD_SABER_ENERGY.get()));
        return new Pair(removed, removeReceiver ? sender : receiver, helper.makeMockPlayer(GameType.SURVIVAL));
    }

    private static void assertRemoved(GameTestHelper helper, Pair pair) {
        BlockPos position = pair.removed().getBlockPos();
        helper.assertTrue(helper.getLevel().getBlockState(position).isAir(), "Wrench removal must not recreate the old beam block");
        helper.assertTrue(helper.getLevel().getBlockEntity(position) == null, "No replacement beam entity may survive removal");
        helper.assertTrue(pair.removed().isRemoved() && pair.removed().getMainNode().getNode() == null,
                "The old endpoint and its AE node must be retired");
        helper.assertValueEqual(pair.peer().beamState().connectionCount(), 0, "The surviving peer must discard the detached edge");
        helper.assertTrue(pair.peer().beamState().visuals().isEmpty(), "The peer must not retain a stale beam target");
        helper.assertValueEqual(pair.player().getInventory().countItem(DEBlocks.ME_BEAM_FORMER.get().asItem()), 1,
                "The player must receive exactly one beam block");
        helper.assertValueEqual(pair.player().getInventory().countItem(DEItems.CARD_SABER_ENERGY.get()), 1,
                "The installed upgrade must be returned exactly once");
    }

    private record Pair(BeamFormerBlockEntity removed, BeamFormerBlockEntity peer, Player player) {}
}
