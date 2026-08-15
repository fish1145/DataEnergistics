package com.fish_dan_.data_energistics.entity.explosive;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Verifies that a primed digital annihilator owns and releases its server chunk ticket.
 */
@GameTestHolder(Data_Energistics.MODID)
@PrefixGameTestTemplate(false)
public final class DataNukePrimedEntityGameTest {

    private static final int DISTANT_CHUNK_OFFSET = 128 * 16;
    private static final int TEST_ISOLATION_OFFSET = 32 * 16;
    private static final int CROSS_CHUNK_OFFSET = 2 * 16;
    private static final int LONG_FUSE_TICKS = 1200;
    private static final String TAG_ACTIVE = "DataNukeActive";

    private DataNukePrimedEntityGameTest() {}

    @TestHolder("digital_annihilator_force_loads_its_chunk_until_removed")
    @EmptyTemplate("5x5")
    @GameTest(template = "empty_5x5", timeoutTicks = 400)
    public static void forceLoadsItsChunkUntilRemoved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = distantOrigin(helper, 0);
        level.getChunkAt(origin);

        DataNukePrimedEntity entity = createStationaryEntity(level, origin, LONG_FUSE_TICKS);

        ChunkPos chunkPos = new ChunkPos(origin);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(
                        isForceTicked(level, chunkPos),
                        "The preloaded chunk must not have a force-ticking ticket before the digital annihilator is added"))
                .thenExecute(() -> helper.assertTrue(
                        level.addFreshEntity(entity),
                        "The digital annihilator must be added to the test level"))
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            isForceTicked(level, chunkPos),
                            "The digital annihilator must force-tick its current chunk");
                    helper.assertTrue(
                            level.getChunkSource().isPositionTicking(chunkPos.toLong()),
                            "The digital annihilator must keep its current chunk entity-ticking");
                })
                .thenExecute(entity::discard)
                .thenWaitUntil(() -> helper.assertFalse(
                        isForceTicked(level, chunkPos),
                        "Removing the digital annihilator must release its chunk ticket"))
                .thenSucceed();
    }

    @TestHolder("digital_annihilator_moves_its_force_load_ticket")
    @EmptyTemplate("5x5")
    @GameTest(template = "empty_5x5", timeoutTicks = 400)
    public static void movesItsForceLoadTicket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = distantOrigin(helper, 1);
        BlockPos destination = origin.offset(CROSS_CHUNK_OFFSET, 0, 0);
        level.getChunkAt(origin);
        level.getChunkAt(destination);

        DataNukePrimedEntity entity = createStationaryEntity(level, origin, LONG_FUSE_TICKS);

        ChunkPos originChunk = new ChunkPos(origin);
        ChunkPos destinationChunk = new ChunkPos(destination);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertFalse(
                            isForceTicked(level, originChunk),
                            "The preloaded origin chunk must not have a force-ticking ticket before the digital annihilator is added");
                    helper.assertFalse(
                            isForceTicked(level, destinationChunk),
                            "The preloaded destination chunk must not have a force-ticking ticket before the digital annihilator is added");
                })
                .thenExecute(() -> helper.assertTrue(
                        level.addFreshEntity(entity),
                        "The digital annihilator must be added to the test level"))
                .thenWaitUntil(() -> {
                    helper.assertTrue(
                            isForceTicked(level, originChunk),
                            "The digital annihilator must initially force-tick its origin chunk");
                    helper.assertTrue(
                            level.getChunkSource().isPositionTicking(originChunk.toLong()),
                            "The digital annihilator must initially force-load its origin chunk");
                    helper.assertFalse(
                            isForceTicked(level, destinationChunk),
                            "The unused destination chunk must not have a force-ticking ticket");
                })
                .thenExecute(() -> entity.setPos(destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D))
                .thenWaitUntil(() -> {
                    helper.assertFalse(
                            isForceTicked(level, originChunk),
                            "Moving the digital annihilator must release its previous chunk");
                    helper.assertTrue(
                            isForceTicked(level, destinationChunk),
                            "Moving the digital annihilator must force-tick its destination chunk");
                    helper.assertTrue(
                            level.getChunkSource().isPositionTicking(destinationChunk.toLong()),
                            "Moving the digital annihilator must force-load its destination chunk");
                })
                .thenExecute(entity::discard)
                .thenWaitUntil(() -> helper.assertFalse(
                        isForceTicked(level, destinationChunk),
                        "Removing the moved digital annihilator must release its destination chunk"))
                .thenSucceed();
    }

    @TestHolder("digital_annihilator_force_loads_its_chunk_while_active")
    @EmptyTemplate("5x5")
    @GameTest(template = "empty_5x5", timeoutTicks = 400)
    public static void forceLoadsItsChunkWhileActive(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = distantOrigin(helper, 2);
        level.getChunkAt(origin);

        DataNukePrimedEntity entity = createStationaryEntity(level, origin, LONG_FUSE_TICKS);
        CompoundTag savedEntity = entity.saveWithoutId(new CompoundTag());
        savedEntity.putBoolean(TAG_ACTIVE, true);
        entity.load(savedEntity);
        helper.assertTrue(entity.isActive(), "The restored digital annihilator must be active");

        ChunkPos chunkPos = new ChunkPos(origin);
        AtomicBoolean observedActiveTicket = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (!entity.isAddedToLevel() || entity.isRemoved() || !isForceTicked(level, chunkPos) || !level.getChunkSource().isPositionTicking(chunkPos.toLong())) {
                return;
            }
            observedActiveTicket.set(true);
            entity.discard();
        });

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertFalse(
                        isForceTicked(level, chunkPos),
                        "The preloaded chunk must not have a force-ticking ticket before the active digital annihilator is added"))
                .thenExecute(() -> helper.assertTrue(
                        level.addFreshEntity(entity),
                        "The active digital annihilator must be added to the test level"))
                .thenWaitUntil(() -> helper.assertTrue(
                        observedActiveTicket.get(),
                        "The active digital annihilator must keep its chunk entity-ticking"))
                .thenWaitUntil(() -> helper.assertFalse(
                        isForceTicked(level, chunkPos),
                        "Removing the active digital annihilator must release its origin chunk"))
                .thenSucceed();
    }

    @TestHolder("digital_annihilators_share_their_force_load_ticket")
    @EmptyTemplate("5x5")
    @GameTest(template = "empty_5x5", timeoutTicks = 400)
    public static void shareTheirForceLoadTicket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = distantOrigin(helper, 3);
        level.getChunkAt(origin);

        DataNukePrimedEntity first = createStationaryEntity(level, origin, LONG_FUSE_TICKS);
        DataNukePrimedEntity second = createStationaryEntity(level, origin, LONG_FUSE_TICKS);

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

    private static DataNukePrimedEntity createStationaryEntity(ServerLevel level, BlockPos origin, int fuseTicks) {
        DataNukePrimedEntity entity = new DataNukePrimedEntity(level, origin, null);
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setFuse(fuseTicks);
        return entity;
    }
}
