package com.fish_dan_.data_energistics.blockentity.orbital.astronomy;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.ae2.key.CelestialEnergyKey;
import com.fish_dan_.data_energistics.block.orbital.astronomy.AstronomicalObservatoryBlock;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.astronomy.AstronomyDimensionRules;
import com.fish_dan_.data_energistics.registry.DEBlockEntities;
import com.fish_dan_.data_energistics.registry.DEBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.orientation.BlockOrientation;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Performs the low-tier observatory's server-authoritative AE energy-to-Celestial Energy transaction.
 */
public final class AstronomicalObservatoryBlockEntity extends AENetworkedBlockEntity {

    private static final double ENERGY_TOLERANCE = 0.0001D;

    private boolean runtimeFaultLogged;
    private boolean insertionMismatchLogged;

    public AstronomicalObservatoryBlockEntity(BlockPos pos, BlockState state) {
        super(DEBlockEntities.ASTRONOMICAL_OBSERVATORY_BLOCK_ENTITY.get(), pos, state);
        getMainNode()
                .setFlags(GridFlags.REQUIRE_CHANNEL)
                .setVisualRepresentation(DEBlocks.ASTRONOMICAL_OBSERVATORY.get())
                .setIdlePowerUsage(0.0D);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.COVERED;
    }

    /**
     * Runs one complete observation attempt on the logical server thread.
     */
    public void serverTick() {
        boolean producing;
        try {
            producing = tryObserve();
            if (this.runtimeFaultLogged) {
                Data_Energistics.LOGGER.info("Recovered astronomical observatory at {}", this.worldPosition);
                this.runtimeFaultLogged = false;
            }
        } catch (RuntimeException exception) {
            producing = false;
            if (!this.runtimeFaultLogged) {
                Data_Energistics.LOGGER.error(
                        "Astronomical observatory at {} failed its observation tick",
                        this.worldPosition,
                        exception);
                this.runtimeFaultLogged = true;
            }
        }
        updateBlockState(producing);
    }

    private boolean tryObserve() {
        if (!(this.level instanceof ServerLevel serverLevel)) {
            return false;
        }
        DataEnergisticsSettings.Astronomy settings = DataEnergisticsConfiguration.INSTANCE.astronomy();
        if (!AstronomyDimensionRules.isObservable(serverLevel) ||
                !AstronomyDimensionRules.isObservationWindowOpen(serverLevel, settings) ||
                serverLevel.isThundering() ||
                !serverLevel.canSeeSky(this.worldPosition.above())) {
            return false;
        }

        long celestialEnergy = AstronomyDimensionRules.celestialEnergyPerTick(serverLevel, settings);
        if (celestialEnergy == 0L || !getMainNode().isActive()) {
            return false;
        }
        IGrid grid = getMainNode().getGrid();
        if (grid == null) {
            throw new IllegalStateException("Active astronomical observatory lost its AE grid");
        }

        MEStorage storage = grid.getStorageService().getInventory();
        IActionSource actionSource = IActionSource.ofMachine(this);
        long insertable = storage.insert(
                CelestialEnergyKey.of(),
                celestialEnergy,
                Actionable.SIMULATE,
                actionSource);
        requireValidInsertion(insertable, celestialEnergy, "simulated");
        if (insertable < celestialEnergy) {
            return false;
        }

        IEnergyService energyService = grid.getEnergyService();
        double requiredEnergy = settings.lowTierAeEnergyPerTick();
        double simulatedEnergy = energyService.extractAEPower(
                requiredEnergy,
                Actionable.SIMULATE,
                PowerMultiplier.ONE);
        if (!containsRequiredEnergy(simulatedEnergy, requiredEnergy)) {
            return false;
        }

        double extractedEnergy = energyService.extractAEPower(
                requiredEnergy,
                Actionable.MODULATE,
                PowerMultiplier.ONE);
        if (!containsRequiredEnergy(extractedEnergy, requiredEnergy)) {
            refundEnergy(energyService, extractedEnergy);
            return false;
        }

        long inserted;
        try {
            inserted = storage.insert(
                    CelestialEnergyKey.of(),
                    celestialEnergy,
                    Actionable.MODULATE,
                    actionSource);
        } catch (RuntimeException insertionFailure) {
            try {
                refundEnergy(energyService, extractedEnergy);
            } catch (RuntimeException refundFailure) {
                insertionFailure.addSuppressed(refundFailure);
            }
            throw insertionFailure;
        }
        try {
            requireValidInsertion(inserted, celestialEnergy, "actual");
        } catch (RuntimeException invalidInsertion) {
            try {
                refundEnergy(energyService, extractedEnergy);
            } catch (RuntimeException refundFailure) {
                invalidInsertion.addSuppressed(refundFailure);
            }
            throw invalidInsertion;
        }
        if (inserted < celestialEnergy) {
            double consumedFraction = (double) inserted / celestialEnergy;
            refundEnergy(energyService, extractedEnergy * (1.0D - consumedFraction));
            if (!this.insertionMismatchLogged) {
                Data_Energistics.LOGGER.warn(
                        "Astronomical observatory at {} accepted only {} of {} simulated Celestial Energy",
                        this.worldPosition,
                        inserted,
                        celestialEnergy);
                this.insertionMismatchLogged = true;
            }
            return inserted > 0L;
        }
        if (this.insertionMismatchLogged) {
            Data_Energistics.LOGGER.info(
                    "Recovered Celestial Energy insertion for astronomical observatory at {}",
                    this.worldPosition);
            this.insertionMismatchLogged = false;
        }
        return true;
    }

    private static boolean containsRequiredEnergy(double extracted, double required) {
        if (!Double.isFinite(extracted) || extracted < 0.0D) {
            throw new IllegalStateException("AE grid returned an invalid extracted energy amount: " + extracted);
        }
        return extracted + ENERGY_TOLERANCE >= required;
    }

    private static void requireValidInsertion(long inserted, long offered, String phase) {
        if (inserted < 0L || inserted > offered) {
            throw new IllegalStateException(
                    "AE storage returned an invalid " + phase + " Celestial Energy insertion: " + inserted);
        }
    }

    private static void refundEnergy(IEnergyService energyService, double energy) {
        if (energy <= ENERGY_TOLERANCE) {
            return;
        }
        double overflow = energyService.injectPower(energy, Actionable.MODULATE);
        if (!Double.isFinite(overflow) || overflow > ENERGY_TOLERANCE) {
            throw new IllegalStateException("AE grid rejected an observation energy refund of " + energy);
        }
    }

    private void updateBlockState(boolean producing) {
        if (this.level == null) {
            return;
        }
        BlockState state = this.level.getBlockState(this.worldPosition);
        if (state.getBlock() instanceof AstronomicalObservatoryBlock &&
                state.getValue(AstronomicalObservatoryBlock.LIT) != producing) {
            this.level.setBlock(
                    this.worldPosition,
                    state.setValue(AstronomicalObservatoryBlock.LIT, producing),
                    Block.UPDATE_ALL);
        }
    }
}
