package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TuningForkBlockEntity;
import com.fish_dan_.data_energistics.common.resonance.OrderedBlockPath;
import com.fish_dan_.data_energistics.common.resonance.TuningForkVariant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Emits a periodic resonance wave toward one uniformly selected tuning fork in the loaded local area.
 */
public class ResonanceCrystalBlock extends Block {

    private static final int WAVE_INTERVAL_TICKS = 1000;
    private static final int SEARCH_RADIUS = 3;

    public ResonanceCrystalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(this)) {
            level.scheduleTick(pos, this, WAVE_INTERVAL_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        try {
            emitWave(level, pos, random);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to emit resonance-crystal wave: dimension={}, source={}",
                    level.dimension().location(),
                    pos,
                    exception);
        } finally {
            if (level.getBlockState(pos).is(this)) {
                level.scheduleTick(pos, this, WAVE_INTERVAL_TICKS);
            }
        }
    }

    private static void emitWave(ServerLevel level, BlockPos sourcePos, RandomSource random) {
        List<BlockPos> candidates = collectLoadedTuningForks(level, sourcePos);
        if (candidates.isEmpty()) {
            return;
        }

        BlockPos targetPos = candidates.get(random.nextInt(candidates.size()));
        if (!isPathClear(level, sourcePos, targetPos)) {
            return;
        }

        LevelChunk targetChunk = getLoadedChunk(level, targetPos);
        if (targetChunk == null) {
            return;
        }
        BlockState forkState = targetChunk.getBlockState(targetPos);
        if (!(forkState.getBlock() instanceof TuningForkBlock tuningForkBlock)) {
            return;
        }
        if (!(targetChunk.getBlockEntity(targetPos) instanceof TuningForkBlockEntity tuningFork)) {
            throw new IllegalStateException("Tuning fork at " + targetPos + " has no matching block entity");
        }

        TuningForkVariant variant = tuningForkBlock.getVariant();
        showWave(level, sourcePos, targetPos);
        try {
            tuningFork.processWave(random, false);
        } finally {
            level.gameEvent(variant.gameEvent(), Vec3.atCenterOf(targetPos), GameEvent.Context.of(forkState));
        }
    }

    private static List<BlockPos> collectLoadedTuningForks(ServerLevel level, BlockPos sourcePos) {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -SEARCH_RADIUS; x <= SEARCH_RADIUS; x++) {
            for (int y = -SEARCH_RADIUS; y <= SEARCH_RADIUS; y++) {
                for (int z = -SEARCH_RADIUS; z <= SEARCH_RADIUS; z++) {
                    cursor.setWithOffset(sourcePos, x, y, z);
                    LevelChunk chunk = getLoadedChunk(level, cursor);
                    if (chunk != null && chunk.getBlockState(cursor).getBlock() instanceof TuningForkBlock) {
                        candidates.add(cursor.immutable());
                    }
                }
            }
        }
        return candidates;
    }

    private static boolean isPathClear(ServerLevel level, BlockPos sourcePos, BlockPos targetPos) {
        return OrderedBlockPath.visit(
                Vec3.atCenterOf(sourcePos),
                Vec3.atCenterOf(targetPos),
                false,
                false,
                pos -> {
                    LevelChunk chunk = getLoadedChunk(level, pos);
                    return chunk != null && !chunk.getBlockState(pos).is(BlockTags.OCCLUDES_VIBRATION_SIGNALS);
                });
    }

    private static void showWave(ServerLevel level, BlockPos sourcePos, BlockPos targetPos) {
        Vec3 source = Vec3.atCenterOf(sourcePos);
        int travelTicks = Math.max(1, Mth.floor(source.distanceTo(Vec3.atCenterOf(targetPos))));
        level.sendParticles(
                new VibrationParticleOption(new BlockPositionSource(targetPos), travelTicks),
                source.x,
                source.y,
                source.z,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D);
        level.playSound(null, sourcePos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    @Nullable
    private static LevelChunk getLoadedChunk(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
    }
}
