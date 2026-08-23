package com.fish_dan_.data_energistics.orbital.control.session;

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
import com.fish_dan_.data_energistics.orbital.control.OrbitalAttackPreviewEstimate;
import com.fish_dan_.data_energistics.orbital.reserve.OrbitalEnergyReserve;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/**
 * Server-thread cursor that captures one attack preview without scanning all affected chunks in one tick.
 *
 * <p>The immutable cost, reserve and workload values are frozen by {@link #begin}; only the rectangular chunk cursor
 * and its two result counters change afterwards. Chunk inspection uses {@code getChunkNow}, so advancing this cursor
 * never loads or generates terrain.</p>
 */
public final class OrbitalAttackPreviewCalculation {

    private final MinecraftServer server;
    private final ResourceKey<Level> dimension;
    private final BlockPos target;
    private final OrbitalAttackCost cost;
    private final long availableCelestialEnergy;
    private final long availableAeEnergy;
    private final long scheduledCoordinates;
    private final long scheduledBlocks;
    private final int effectRadius;
    private final long minimumExecutionTicks;
    private final int minimumChunkX;
    private final int minimumChunkZ;
    private final int chunkRows;
    final int totalChunkCount;

    int checkedChunkCount;
    private int affectedChunkCount;
    private int unloadedChunkCount;

    private OrbitalAttackPreviewCalculation(
                                            MinecraftServer server,
                                            ResourceKey<Level> dimension,
                                            BlockPos target,
                                            OrbitalAttackCost cost,
                                            long availableCelestialEnergy,
                                            long availableAeEnergy,
                                            long scheduledCoordinates,
                                            long scheduledBlocks,
                                            int effectRadius,
                                            long minimumExecutionTicks,
                                            int minimumChunkX,
                                            int minimumChunkZ,
                                            int chunkRows,
                                            int totalChunkCount) {
        this.server = server;
        this.dimension = dimension;
        this.target = target.immutable();
        this.cost = cost;
        this.availableCelestialEnergy = availableCelestialEnergy;
        this.availableAeEnergy = availableAeEnergy;
        this.scheduledCoordinates = scheduledCoordinates;
        this.scheduledBlocks = scheduledBlocks;
        this.effectRadius = effectRadius;
        this.minimumExecutionTicks = minimumExecutionTicks;
        this.minimumChunkX = minimumChunkX;
        this.minimumChunkZ = minimumChunkZ;
        this.chunkRows = chunkRows;
        this.totalChunkCount = totalChunkCount;
    }

    /**
     * Freezes all non-chunk estimate inputs and creates an unadvanced rectangular scan cursor.
     */
    public static OrbitalAttackPreviewCalculation begin(
                                                        DataEnergisticsConfiguration configuration,
                                                        ServerLevel level,
                                                        BlockPos target,
                                                        OrbitalAttackMode mode,
                                                        int directedRadius,
                                                        @Nullable OrbitalDirectedEnergyDepth directedDepth,
                                                        OrbitalEnergyReserve reserve) {
        requireServerThread(level.getServer());
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
                workingTicks = divideRoundingUp(scheduledBlocks, settings.maxAttackBlockMutationsPerTaskTick);
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
                workingTicks = divideRoundingUp(scheduledBlocks, settings.maxAttackBlockMutationsPerTaskTick);
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

        long minimumChunkX = Math.floorDiv((long) target.getX() - effectRadius, 16L);
        long maximumChunkX = Math.floorDiv((long) target.getX() + effectRadius, 16L);
        long minimumChunkZ = Math.floorDiv((long) target.getZ() - effectRadius, 16L);
        long maximumChunkZ = Math.floorDiv((long) target.getZ() + effectRadius, 16L);
        int chunkColumns = Math.toIntExact(maximumChunkX - minimumChunkX + 1L);
        int chunkRows = Math.toIntExact(maximumChunkZ - minimumChunkZ + 1L);
        int totalChunkCount = Math.toIntExact(Math.multiplyExact((long) chunkColumns, chunkRows));
        return new OrbitalAttackPreviewCalculation(
                level.getServer(),
                level.dimension(),
                target,
                cost,
                reserve.celestialEnergy(),
                reserve.aeEnergy(),
                scheduledCoordinates,
                scheduledBlocks,
                effectRadius,
                Math.addExact(settings.attackWarningTicks, workingTicks),
                Math.toIntExact(minimumChunkX),
                Math.toIntExact(minimumChunkZ),
                chunkRows,
                totalChunkCount);
    }

    /**
     * Checks at most {@code budget} rectangular chunk positions and returns the budget actually consumed.
     */
    public int advance(ServerLevel level, int budget) {
        requireServerThread(this.server);
        if (level.getServer() != this.server || !level.dimension().equals(this.dimension)) {
            throw new IllegalArgumentException("Orbital preview calculation advanced with a different level");
        }
        if (budget <= 0) {
            throw new IllegalArgumentException("Orbital preview chunk-check budget must be positive");
        }
        int checkedAtStart = this.checkedChunkCount;
        int stopAt = (int) Math.min((long) this.totalChunkCount, (long) checkedAtStart + budget);
        while (this.checkedChunkCount < stopAt) {
            int cursor = this.checkedChunkCount++;
            int chunkX = this.minimumChunkX + cursor / this.chunkRows;
            int chunkZ = this.minimumChunkZ + cursor % this.chunkRows;
            if (!intersectsCircle(chunkX, chunkZ, this.target, this.effectRadius)) {
                continue;
            }
            this.affectedChunkCount++;
            if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                this.unloadedChunkCount++;
            }
        }
        return this.checkedChunkCount - checkedAtStart;
    }

    public boolean complete() {
        return this.checkedChunkCount == this.totalChunkCount;
    }

    /** Returns the immutable estimate after all chunk positions have been checked. */
    public OrbitalAttackPreviewEstimate finish() {
        requireServerThread(this.server);
        if (!complete()) {
            throw new IllegalStateException("Cannot finish an incomplete orbital attack preview calculation");
        }
        return new OrbitalAttackPreviewEstimate(
                this.cost,
                this.availableCelestialEnergy,
                this.availableAeEnergy,
                this.scheduledCoordinates,
                this.scheduledBlocks,
                this.effectRadius,
                this.affectedChunkCount,
                this.unloadedChunkCount,
                this.minimumExecutionTicks);
    }

    private static boolean intersectsCircle(int chunkX, int chunkZ, BlockPos target, int radius) {
        long minimumBlockX = (long) chunkX << 4;
        long minimumBlockZ = (long) chunkZ << 4;
        long closestX = Math.clamp((long) target.getX(), minimumBlockX, minimumBlockX + 15L);
        long closestZ = Math.clamp((long) target.getZ(), minimumBlockZ, minimumBlockZ + 15L);
        long offsetX = closestX - target.getX();
        long offsetZ = closestZ - target.getZ();
        return offsetX * offsetX + offsetZ * offsetZ <= (long) radius * radius;
    }

    private static long divideRoundingUp(long value, int divisor) {
        return value == 0L ? 0L : 1L + (value - 1L) / divisor;
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("Orbital preview calculations may only run on the server thread");
        }
    }
}
