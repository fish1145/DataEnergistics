package com.fish_dan_.data_energistics.blockentity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.registry.DEBlocks;

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

import appeng.api.networking.GridFlags;
import appeng.api.util.AECableType;

import java.util.Set;

@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class TuningForkBaseAeConnectivityGameTest {

    private static final BlockPos BASE_POS = new BlockPos(2, 2, 2);

    private TuningForkBaseAeConnectivityGameTest() {}

    @TestHolder("tuning_fork_base_relays_ae_channels")
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", timeoutTicks = 100)
    public static void exposesAeCableConnections(GameTestHelper helper) {
        helper.setBlock(BASE_POS, DEBlocks.TUNING_FORK_BASE.get());
        TuningForkBaseBlockEntity base = requireBase(helper);

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(
                        base.getMainNode().getNode() != null,
                        "The tuning fork base managed node did not become ready"))
                .thenExecute(() -> assertChannelRelayConfiguration(helper, base))
                .thenSucceed();
    }

    private static TuningForkBaseBlockEntity requireBase(GameTestHelper helper) {
        BlockEntity blockEntity = helper.getBlockEntity(BASE_POS);
        if (blockEntity instanceof TuningForkBaseBlockEntity base) {
            return base;
        }
        throw new GameTestAssertException("Placed tuning fork base has no matching block entity");
    }

    private static void assertChannelRelayConfiguration(GameTestHelper helper, TuningForkBaseBlockEntity base) {
        helper.assertTrue(
                base.getMainNode().getNode().hasFlag(GridFlags.REQUIRE_CHANNEL),
                "The tuning fork base must reserve one channel for its own AE device");
        helper.assertTrue(
                base.getMainNode().getNode().hasFlag(GridFlags.PREFERRED),
                "The tuning fork base must prefer cable paths so it can relay AE channels");
        helper.assertFalse(
                base.getMainNode().getNode().hasFlag(GridFlags.CANNOT_CARRY),
                "The tuning fork base must not block AE channel propagation");
        Set<Direction> exposedSides = base.getGridConnectableSides(null);
        for (Direction side : Direction.values()) {
            helper.assertTrue(exposedSides.contains(side), "The tuning fork base must expose its " + side + " side to ME cables");
            helper.assertValueEqual(
                    base.getCableConnectionType(side),
                    AECableType.COVERED,
                    "The tuning fork base must accept standard and smart ME cables on its " + side + " side");
            helper.assertTrue(base.getGridNode(side) != null, "The tuning fork base must provide a grid node on its " + side + " side");
        }
    }
}
