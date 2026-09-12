package com.fish_dan_.data_energistics.entity.explosive;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

/**
 * Verifies digital-annihilator chunk-ticket ownership and lifecycle cleanup.
 */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataNukePrimedEntityGameTest {

    private static final int DISTANT_CHUNK_OFFSET = 128 * 16;
    private static final int TEST_ISOLATION_OFFSET = 32 * 16;
    private static final int LONG_FUSE_TICKS = Integer.MAX_VALUE;

    private DataNukePrimedEntityGameTest() {}

    @TestHolder("digital_annihilators_share_their_force_load_ticket")
    @EmptyTemplate("5x5")
    @GameTest(template = "empty_5x5", timeoutTicks = 400)
    public static void shareTheirForceLoadTicket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = distantOrigin(helper, 3);
        level.getChunkAt(origin);

        DataNukePrimedEntity first = createStationaryEntity(level, origin);
        DataNukePrimedEntity second = createStationaryEntity(level, origin);

        ChunkPos chunkPos = new ChunkPos(origin);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(
                        isForceTicked(level, chunkPos),
                        "The preloaded chunk must not have a force-ticking ticket before the digital annihilators are added"))
                .thenExecute(() -> {
                    helper.assertTrue(
                            level.addFreshEntity(first),
                            "The first digital annihilator must be added to the test level");
                    helper.assertTrue(
                            level.addFreshEntity(second),
                            "The second digital annihilator must be added to the test level");
                })
                .thenWaitUntil(() -> helper.assertTrue(
                        isForceTicked(level, chunkPos),
                        "The digital annihilators must force-tick their shared chunk"))
                .thenExecute(first::discard)
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(
                            isForceTicked(level, chunkPos),
                            "Removing one digital annihilator must preserve the shared chunk ticket");
                    second.discard();
                })
                .thenWaitUntil(() -> helper.assertFalse(
                        isForceTicked(level, chunkPos),
                        "Removing the final digital annihilator must release the shared chunk ticket"))
                .thenSucceed();
    }

    private static BlockPos distantOrigin(GameTestHelper helper, int isolationLane) {
        return helper.absolutePos(new BlockPos(2, 3, 2))
                .offset(DISTANT_CHUNK_OFFSET, 0, TEST_ISOLATION_OFFSET * isolationLane);
    }

    private static boolean isForceTicked(ServerLevel level, ChunkPos chunkPos) {
        return level.getChunkSource().chunkMap.getDistanceManager().shouldForceTicks(chunkPos.toLong());
    }

    private static DataNukePrimedEntity createStationaryEntity(ServerLevel level, BlockPos origin) {
        DataNukePrimedEntity entity = new DataNukePrimedEntity(level, origin, null);
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setFuse(LONG_FUSE_TICKS);
        return entity;
    }
}
