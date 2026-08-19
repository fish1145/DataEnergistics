package com.fish_dan_.data_energistics.common.resonance;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.TuningForkBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Predicate;

/**
 * Applies tuning-fork and crystal effects along a direct Warden sonic-boom path.
 */
@ApiStatus.Internal
public final class WardenSonicBoomResonance {

    private static final double EXTENSION_BEYOND_TARGET = 7.0D;

    private WardenSonicBoomResonance() {}

    /**
     * Reconstructs the vanilla chest-to-eye ray and extends it seven blocks beyond the original target.
     *
     * @return {@code true} when a tuning fork no farther than the target accepted Echo and intercepted the attack
     */
    public static boolean process(ServerLevel level, Warden warden, LivingEntity target) {
        Vec3 start = warden.position();
        Vec3 end = target.getEyePosition();
        BlockPos fallbackPosition = warden.blockPosition();
        try {
            start = start.add(warden.getAttachments().get(EntityAttachment.WARDEN_CHEST, 0, warden.getYRot()));
            Vec3 direction = end.subtract(start).normalize();
            end = end.add(direction.scale(EXTENSION_BEYOND_TARGET));
            SonicPathVisitor visitor = new SonicPathVisitor(level, warden, target, start, end);
            try {
                OrderedBlockPath.visit(start, end, true, true, visitor);
            } catch (RuntimeException exception) {
                visitor.logFailure(visitor.currentPosition, exception);
            }
            return visitor.attackIntercepted;
        } catch (RuntimeException exception) {
            logFailure(level, warden, target, start, end, fallbackPosition, exception);
            return false;
        }
    }

    private static void logFailure(ServerLevel level, Warden warden, LivingEntity target, Vec3 start, Vec3 end,
                                   BlockPos blockPosition, RuntimeException exception) {
        Data_Energistics.LOGGER.error(
                "Failed to process Warden sonic-boom resonance: dimension={}, warden={} at {}, target={} at {}, start={}, end={}, block={}",
                level.dimension().location(),
                warden.getStringUUID(),
                warden.blockPosition(),
                target.getStringUUID(),
                target.blockPosition(),
                start,
                end,
                blockPosition,
                exception);
    }

    private static final class SonicPathVisitor implements Predicate<BlockPos> {

        private final ServerLevel level;
        private final Warden warden;
        private final LivingEntity target;
        private final Vec3 start;
        private final Vec3 end;
        private final BlockPos targetPosition;
        private BlockPos currentPosition;
        private boolean tuningForkHandled;
        private boolean crystalHandled;
        private boolean targetReached;
        private boolean attackIntercepted;

        private SonicPathVisitor(ServerLevel level, Warden warden, LivingEntity target, Vec3 start, Vec3 end) {
            this.level = level;
            this.warden = warden;
            this.target = target;
            this.start = start;
            this.end = end;
            this.targetPosition = BlockPos.containing(target.getEyePosition());
            this.currentPosition = BlockPos.containing(start);
        }

        @Override
        public boolean test(BlockPos pos) {
            this.currentPosition = pos;
            boolean beforeOrAtTarget = !this.targetReached;
            try {
                LevelChunk chunk = this.level.getChunkSource().getChunkNow(
                        SectionPos.blockToSectionCoord(pos.getX()),
                        SectionPos.blockToSectionCoord(pos.getZ()));
                if (chunk != null) {
                    BlockState state = chunk.getBlockState(pos);
                    if (!this.tuningForkHandled && state.getBlock() instanceof TuningForkBlock) {
                        this.tuningForkHandled = true;
                        if (TuningForkWaveHit.process(chunk, pos, this.level.getRandom(), true) && beforeOrAtTarget) {
                            this.attackIntercepted = true;
                        }
                    }
                    if (!this.crystalHandled && ResonanceCrystalWaveTransformation.isWardenChangeable(state)) {
                        this.crystalHandled = true;
                        ResonanceCrystalWaveTransformation.transformFromWarden(this.level, pos, state);
                    }
                }
            } catch (RuntimeException exception) {
                logFailure(pos, exception);
            }
            if (pos.equals(this.targetPosition)) {
                this.targetReached = true;
            }
            return !this.tuningForkHandled || !this.crystalHandled;
        }

        private void logFailure(BlockPos pos, RuntimeException exception) {
            WardenSonicBoomResonance.logFailure(
                    this.level,
                    this.warden,
                    this.target,
                    this.start,
                    this.end,
                    pos,
                    exception);
        }
    }
}
