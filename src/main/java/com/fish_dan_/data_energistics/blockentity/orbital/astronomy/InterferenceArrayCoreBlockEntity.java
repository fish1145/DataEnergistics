package com.fish_dan_.data_energistics.blockentity.orbital.astronomy;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.orbital.astronomy.InterferenceArrayCoreBlock;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.astronomy.AstronomyDimensionRules;
import com.fish_dan_.data_energistics.orbital.astronomy.CelestialEnergyGridTransaction;
import com.fish_dan_.data_energistics.orbital.astronomy.InterferenceArrayPattern;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Claims valid mirrors and performs the high-tier array's server-authoritative production transaction.
 */
public final class InterferenceArrayCoreBlockEntity extends AENetworkedBlockEntity {

    private static final String CLAIMED_MIRRORS_TAG = "claimedMirrors";
    private static final int STRUCTURE_SCAN_INTERVAL_TICKS = 20;
    private static final int PERSISTED_MIRROR_LIMIT = 16;

    private Set<BlockPos> claimedMirrors = Set.of();
    private long nextStructureScan;
    private boolean runtimeFaultLogged;
    private boolean insertionMismatchLogged;

    public InterferenceArrayCoreBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.INTERFERENCE_ARRAY_CORE_BLOCK_ENTITY.get(), pos, state);
        getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setVisualRepresentation(DEBlocks.INTERFERENCE_ARRAY_CORE.get())
                .setIdlePowerUsage(0.0D);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.of(Direction.DOWN);
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.COVERED;
    }

    /**
     * Runs structure maintenance and one production attempt on the logical server thread.
     */
    public void serverTick() {
        boolean producing;
        try {
            producing = tryProduce();
            if (this.runtimeFaultLogged) {
                Data_Energistics.LOGGER.info("Recovered interference array core at {}", this.worldPosition);
                this.runtimeFaultLogged = false;
            }
        } catch (RuntimeException exception) {
            producing = false;
            if (!this.runtimeFaultLogged) {
                Data_Energistics.LOGGER.error(
                        "Interference array core at {} failed its production tick",
                        this.worldPosition,
                        exception);
                this.runtimeFaultLogged = true;
            }
        }
        updateBlockState(producing);
    }

    /**
     * Releases every mirror claim retained by this core when the controller block is permanently removed.
     */
    public void releaseMirrorClaims() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (BlockPos mirrorPos : this.claimedMirrors) {
            releaseMirror(serverLevel, mirrorPos);
        }
        if (!this.claimedMirrors.isEmpty()) {
            this.claimedMirrors = Set.of();
            setChanged();
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        long[] persistedMirrors = data.getLongArray(CLAIMED_MIRRORS_TAG);
        LinkedHashSet<BlockPos> loadedMirrors = new LinkedHashSet<>();
        Arrays.stream(persistedMirrors)
                .limit(PERSISTED_MIRROR_LIMIT)
                .mapToObj(BlockPos::of)
                .forEach(loadedMirrors::add);
        this.claimedMirrors = Set.copyOf(loadedMirrors);
        this.nextStructureScan = 0L;
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.putLongArray(
                CLAIMED_MIRRORS_TAG,
                this.claimedMirrors.stream().mapToLong(BlockPos::asLong).toArray());
    }

    private boolean tryProduce() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        DataEnergisticsConfiguration.AstronomySchema settings = DataEnergisticsConfiguration.INSTANCE.astronomy;
        long gameTime = serverLevel.getGameTime();
        if (gameTime >= this.nextStructureScan) {
            refreshMirrorClaims(serverLevel, settings);
            this.nextStructureScan = gameTime + STRUCTURE_SCAN_INTERVAL_TICKS;
        }
        int mirrorCount = this.claimedMirrors.size();
        if (mirrorCount < settings.highTierMinimumMirrors ||
                !AstronomyDimensionRules.isObservable(serverLevel) ||
                !AstronomyDimensionRules.isObservationWindowOpen(serverLevel, settings) ||
                serverLevel.isThundering()) {
            return false;
        }

        long baseOutput = baseCelestialEnergy(settings, mirrorCount);
        long celestialEnergy = AstronomyDimensionRules.celestialEnergyPerTick(serverLevel, settings, baseOutput);
        if (celestialEnergy == 0L || !getMainNode().isActive()) {
            return false;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            throw new IllegalStateException("Active interference array core lost its AE grid");
        }

        long inserted = CelestialEnergyGridTransaction.commit(
                grid,
                IActionSource.ofMachine(this),
                celestialEnergy,
                requiredAeEnergy(settings, mirrorCount));
        if (inserted < celestialEnergy) {
            if (inserted > 0L && !this.insertionMismatchLogged) {
                Data_Energistics.LOGGER.warn(
                        "Interference array core at {} accepted only {} of {} simulated Celestial Energy",
                        this.worldPosition,
                        inserted,
                        celestialEnergy);
                this.insertionMismatchLogged = true;
            }
            return inserted > 0L;
        }
        if (this.insertionMismatchLogged) {
            Data_Energistics.LOGGER.info(
                    "Recovered Celestial Energy insertion for interference array core at {}",
                    this.worldPosition);
            this.insertionMismatchLogged = false;
        }
        return true;
    }

    private void refreshMirrorClaims(
                                     ServerLevel level,
                                     DataEnergisticsConfiguration.AstronomySchema settings) {
        List<BlockPos> candidates = InterferenceArrayPattern.findConnectedMirrors(level, this.worldPosition, settings);
        LinkedHashSet<BlockPos> nextClaims = new LinkedHashSet<>();
        for (BlockPos mirrorPos : candidates) {
            if (nextClaims.size() >= settings.highTierMaximumMirrors) {
                break;
            }
            if (level.getBlockEntity(mirrorPos) instanceof AstronomicalMirrorBlockEntity mirror &&
                    mirror.tryClaim(level, this.worldPosition)) {
                nextClaims.add(mirrorPos.immutable());
            }
        }
        for (BlockPos oldMirror : this.claimedMirrors) {
            if (!nextClaims.contains(oldMirror)) {
                releaseMirror(level, oldMirror);
            }
        }
        Set<BlockPos> immutableClaims = Set.copyOf(nextClaims);
        if (!immutableClaims.equals(this.claimedMirrors)) {
            this.claimedMirrors = immutableClaims;
            setChanged();
        }
    }

    private void releaseMirror(ServerLevel level, BlockPos mirrorPos) {
        if (level.getBlockEntity(mirrorPos) instanceof AstronomicalMirrorBlockEntity mirror) {
            mirror.release(this.worldPosition);
        }
    }

    private static long baseCelestialEnergy(DataEnergisticsConfiguration.AstronomySchema settings, int mirrorCount) {
        long output = 0L;
        for (int mirror = 1; mirror <= mirrorCount; mirror++) {
            long mirrorOutput;
            if (mirror <= 4) {
                mirrorOutput = settings.highTierMirrorCelestialEnergyPerTick1To4;
            } else if (mirror <= 8) {
                mirrorOutput = settings.highTierMirrorCelestialEnergyPerTick5To8;
            } else if (mirror <= 12) {
                mirrorOutput = settings.highTierMirrorCelestialEnergyPerTick9To12;
            } else {
                mirrorOutput = settings.highTierMirrorCelestialEnergyPerTick13To16;
            }
            output = saturatedAdd(output, mirrorOutput);
        }
        return output;
    }

    private static long requiredAeEnergy(DataEnergisticsConfiguration.AstronomySchema settings, int mirrorCount) {
        long mirrorCost = saturatedMultiply(settings.highTierMirrorAeEnergyPerTick, mirrorCount);
        return saturatedAdd(settings.highTierCoreAeEnergyPerTick, mirrorCost);
    }

    private static long saturatedAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long saturatedMultiply(long value, int multiplier) {
        if (value == 0L || multiplier == 0) {
            return 0L;
        }
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }

    private void updateBlockState(boolean producing) {
        if (this.level == null) {
            return;
        }
        BlockState state = this.level.getBlockState(this.worldPosition);
        if (state.getBlock() instanceof InterferenceArrayCoreBlock &&
                state.getValue(InterferenceArrayCoreBlock.LIT) != producing) {
            this.level.setBlock(
                    this.worldPosition,
                    state.setValue(InterferenceArrayCoreBlock.LIT, producing),
                    Block.UPDATE_ALL);
        }
    }
}
