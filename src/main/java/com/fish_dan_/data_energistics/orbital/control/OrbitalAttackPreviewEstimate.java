package com.fish_dan_.data_energistics.orbital.control;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.entity.explosive.DataNukePrimedEntity;
import com.fish_dan_.data_energistics.entity.explosive.DigitalAnnihilationWork;
import com.fish_dan_.data_energistics.entity.projectile.OrbitalAnnihilatorProjectileEntity;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackCost;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackGeometry;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyStrike;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalKineticStrike;
import com.fish_dan_.data_energistics.orbital.reserve.OrbitalEnergyReserve;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import org.jspecify.annotations.Nullable;

/**
 * Server-authoritative cost and workload estimate captured with one attack preview.
 *
 * <p>
 * The execution time is a best-case lower bound. Asynchronous chunk generation and contention for the shared
 * terrain budget can extend it. The unloaded count is an upper bound for newly generated chunks because an existing
 * on-disk chunk may currently be unloaded; calculating that distinction would require blocking chunk-storage I/O.
 * </p>
 */
public record OrbitalAttackPreviewEstimate(
                                           OrbitalAttackCost cost,
                                           long availableCelestialEnergy,
                                           long availableAeEnergy,
                                           long scheduledCoordinates,
                                           long scheduledBlocks,
                                           int effectRadius,
                                           int affectedChunks,
                                           int unloadedChunks,
                                           long minimumExecutionTicks) {

    public OrbitalAttackPreviewEstimate {
        if (availableCelestialEnergy < 0L || availableAeEnergy < 0L || scheduledCoordinates < 0L || scheduledBlocks < 0L || effectRadius < 1 || affectedChunks < 1 || unloadedChunks < 0 || unloadedChunks > affectedChunks || minimumExecutionTicks < 1L) {
            throw new IllegalArgumentException("Invalid orbital attack preview estimate");
        }
    }

    /** Captures a complete estimate from the same immutable configuration snapshot used by the preview revision. */
    public static OrbitalAttackPreviewEstimate capture(
                                                       DataEnergisticsConfiguration configuration,
                                                       ServerLevel level,
                                                       BlockPos target,
                                                       OrbitalAttackMode mode,
                                                       int directedRadius,
                                                       @Nullable OrbitalDirectedEnergyDepth directedDepth,
                                                       OrbitalEnergyReserve reserve) {
        DataEnergisticsConfiguration.OrbitalWeaponSchema settings = configuration.orbitalWeapon;
        OrbitalAttackCost cost;
        long scheduledCoordinates = 0L;
        long scheduledBlocks;
        long workingTicks;
        int effectRadius;
        switch (mode) {
            case KINETIC -> {
                OrbitalAttackGeometry.Kinetic geometry = OrbitalAttackGeometry.Kinetic.fromSettings(settings);
                cost = OrbitalAttackCost.kinetic(settings);
                scheduledBlocks = OrbitalKineticStrike.totalWork(level, target, geometry);
                workingTicks = divideRoundingUp(
                        scheduledBlocks,
                        settings.maxAttackBlockMutationsPerTaskTick);
                effectRadius = geometry.maximumRadius();
            }
            case DIRECTED_ENERGY -> {
                if (directedDepth == null) {
                    throw new IllegalArgumentException("Directed-energy preview depth is required");
                }
                OrbitalDirectedEnergyStrike.validateRadius(directedRadius, settings);
                OrbitalAttackGeometry.DirectedEnergy geometry = OrbitalAttackGeometry.DirectedEnergy.fromSettings(
                        directedRadius,
                        directedDepth,
                        settings);
                scheduledCoordinates = OrbitalDirectedEnergyStrike.scheduledCoordinateCount(directedRadius);
                cost = OrbitalAttackCost.directedEnergy(settings, scheduledCoordinates);
                scheduledBlocks = OrbitalDirectedEnergyStrike.totalWork(level, target, geometry);
                workingTicks = divideRoundingUp(
                        scheduledBlocks,
                        settings.maxAttackBlockMutationsPerTaskTick);
                effectRadius = directedRadius;
            }
            case DIGITAL_ANNIHILATION -> {
                cost = OrbitalAttackCost.digitalAnnihilation(settings);
                DigitalAnnihilationWork.WorkEstimate work = DigitalAnnihilationWork.estimate(
                        level,
                        target,
                        configuration.explosives.dataNuke);
                scheduledBlocks = work.scheduledBlocks();
                workingTicks = Math.addExact(
                        Math.addExact(
                                OrbitalAnnihilatorProjectileEntity.FLIGHT_TICKS,
                                DataNukePrimedEntity.DEFAULT_FUSE_TICKS),
                        work.minimumTicks());
                effectRadius = configuration.explosives.dataNuke.maxRadius;
            }
            default -> throw new IllegalStateException("Unsupported orbital attack mode " + mode);
        }

        ChunkEstimate chunks = countChunks(level, target, effectRadius);
        long minimumExecutionTicks = Math.addExact(settings.attackWarningTicks, workingTicks);
        return new OrbitalAttackPreviewEstimate(
                cost,
                reserve.celestialEnergy(),
                reserve.aeEnergy(),
                scheduledCoordinates,
                scheduledBlocks,
                effectRadius,
                chunks.affected(),
                chunks.unloaded(),
                minimumExecutionTicks);
    }

    /** Returns whether the reserve snapshot shown in this preview can cover both independent escrow resources. */
    public boolean affordable() {
        return this.availableCelestialEnergy >= this.cost.celestialEnergy() && this.availableAeEnergy >= this.cost.aeEnergy();
    }

    private static ChunkEstimate countChunks(ServerLevel level, BlockPos target, int radius) {
        int minimumChunkX = Math.floorDiv(target.getX() - radius, 16);
        int maximumChunkX = Math.floorDiv(target.getX() + radius, 16);
        int minimumChunkZ = Math.floorDiv(target.getZ() - radius, 16);
        int maximumChunkZ = Math.floorDiv(target.getZ() + radius, 16);
        int affected = 0;
        int unloaded = 0;
        for (int chunkX = minimumChunkX; chunkX <= maximumChunkX; chunkX++) {
            for (int chunkZ = minimumChunkZ; chunkZ <= maximumChunkZ; chunkZ++) {
                ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
                if (!intersectsCircle(chunk, target, radius)) {
                    continue;
                }
                affected++;
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    unloaded++;
                }
            }
        }
        return new ChunkEstimate(affected, unloaded);
    }

    private static boolean intersectsCircle(ChunkPos chunk, BlockPos target, int radius) {
        int closestX = Math.clamp(target.getX(), chunk.getMinBlockX(), chunk.getMaxBlockX());
        int closestZ = Math.clamp(target.getZ(), chunk.getMinBlockZ(), chunk.getMaxBlockZ());
        long offsetX = (long) closestX - target.getX();
        long offsetZ = (long) closestZ - target.getZ();
        return offsetX * offsetX + offsetZ * offsetZ <= (long) radius * radius;
    }

    private static long divideRoundingUp(long value, int divisor) {
        return value == 0L ? 0L : 1L + (value - 1L) / divisor;
    }

    private record ChunkEstimate(int affected, int unloaded) {}
}
