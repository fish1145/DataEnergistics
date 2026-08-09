package com.fish_dan_.data_energistics.common.resonance;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.TuningForkBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.ApiStatus;

import java.util.function.Predicate;

/**
 * Lets eligible tuning forks and crystal clusters intercept a validated sculk-sensor vibration path.
 */
@ApiStatus.Internal
public final class SculkVibrationResonance {

    private SculkVibrationResonance() {
    }

    /**
     * Processes only cells between the source and sensor, excluding both endpoint cells.
     *
     * @return {@code true} when the first successful candidate consumed the vibration
     */
    public static boolean intercept(ServerLevel level, Holder<GameEvent> gameEvent, Vec3 source, BlockPos sensorPos) {
        Vec3 sensorCenter = Vec3.atCenterOf(sensorPos);
        VibrationPathVisitor visitor = new VibrationPathVisitor(level, gameEvent, source, sensorPos);
        try {
            OrderedBlockPath.visit(source, sensorCenter, false, false, visitor);
        } catch (RuntimeException exception) {
            visitor.logFailure(visitor.currentPosition, exception);
            return false;
        }
        return visitor.intercepted;
    }

    private static final class VibrationPathVisitor implements Predicate<BlockPos> {

        private final ServerLevel level;
        private final Holder<GameEvent> gameEvent;
        private final Vec3 source;
        private final BlockPos sensorPos;
        private BlockPos currentPosition;
        private boolean intercepted;

        private VibrationPathVisitor(ServerLevel level, Holder<GameEvent> gameEvent, Vec3 source, BlockPos sensorPos) {
            this.level = level;
            this.gameEvent = gameEvent;
            this.source = source;
            this.sensorPos = sensorPos;
            this.currentPosition = BlockPos.containing(source);
        }

        @Override
        public boolean test(BlockPos pos) {
            this.currentPosition = pos;
            try {
                LevelChunk chunk = this.level.getChunkSource().getChunkNow(
                        SectionPos.blockToSectionCoord(pos.getX()),
                        SectionPos.blockToSectionCoord(pos.getZ()));
                if (chunk == null) {
                    return true;
                }

                BlockState state = chunk.getBlockState(pos);
                if (state.getBlock() instanceof TuningForkBlock &&
                        TuningForkWaveHit.process(chunk, pos, this.level.getRandom(), false)) {
                    this.intercepted = true;
                    return false;
                }
                if (ResonanceCrystalWaveTransformation.tryTransformFromVibration(
                        this.level,
                        pos,
                        state,
                        this.level.getRandom())) {
                    this.intercepted = true;
                    return false;
                }
            } catch (RuntimeException exception) {
                logFailure(pos, exception);
            }
            return true;
        }

        private void logFailure(BlockPos pos, RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to process sculk vibration resonance: dimension={}, event={}, source={}, sensor={}, block={}",
                    this.level.dimension().location(),
                    this.gameEvent,
                    this.source,
                    this.sensorPos,
                    pos,
                    exception);
        }
    }
}
