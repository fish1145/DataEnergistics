package com.fish_dan_.data_energistics.blockentity.tower.energy.registry;

import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;
import com.fish_dan_.data_energistics.integration.tower.energy.UnlimitedEnergyAccess.EnergySnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jspecify.annotations.Nullable;

/**
 * One registered strategy for resolving and operating a tower energy endpoint.
 *
 * <p>
 * Mod-specific behavior belongs here; tower orchestration only consumes this contract.
 * </p>
 */
public interface TowerEnergyEndpointIntegration {

    /** Stable diagnostic and registration identifier. */
    String id();

    /**
     * Optional capability lookup. Returning {@code null} lets the registry try the next lookup.
     */
    @Nullable
    default IEnergyStorage findEnergyStorage(Level level, BlockPos position, @Nullable Direction side) {
        return null;
    }

    /** Returns whether this integration owns the already resolved storage route. */
    boolean supports(TowerEnergyEndpointContext context);

    /** Returns the physical backing identity used to merge equivalent access routes. */
    default Object backingIdentity(TowerEnergyEndpointContext context) {
        return context.storage();
    }

    /** Captures the current transfer direction. */
    TowerEnergyDirection direction(TowerEnergyEndpointContext context);

    /** Captures the complete stored amount and capacity in FE-equivalent units. */
    EnergySnapshot snapshot(TowerEnergyEndpointContext context);

    /** Returns the smallest complete extraction unit in FE. */
    default long extractionQuantum(TowerEnergyEndpointContext context) {
        return 1L;
    }

    /** Returns the smallest complete insertion unit in FE. */
    default long insertionQuantum(TowerEnergyEndpointContext context) {
        return 1L;
    }

    /** Executes or simulates an extraction. */
    long extract(TowerEnergyEndpointContext context, long amount, boolean simulate);

    /** Executes or simulates an insertion. */
    long insert(TowerEnergyEndpointContext context, long amount, boolean simulate);

    /** Restores energy removed by the current transaction. */
    long compensateExtraction(TowerEnergyEndpointContext context, long amount);

    /** Whether this route is a virtual buffer with independent budgets. */
    default boolean isBuffer() {
        return false;
    }

    /** Publishes a successful mutation to the owning storage/block. */
    default void publishMutation(TowerEnergyEndpointContext context) {}

    /**
     * Lookup ordering; lower values are attempted first. Operation ordering is registration order.
     */
    default int lookupOrder() {
        return Integer.MAX_VALUE;
    }
}
