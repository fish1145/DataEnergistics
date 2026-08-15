package com.fish_dan_.data_energistics.blockentity.tower.energy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.energy.IEnergyStorage;

import org.jspecify.annotations.Nullable;

/**
 * Describes one FE-capable endpoint visible to a Data Distribution Tower.
 *
 * @param pos       block position that owns the energy storage
 * @param side      queried side, or null for internal access
 * @param storage   resolved energy storage instance
 * @param direction capability permissions captured for the current server tick
 */
public record TowerEnergyEndpoint(BlockPos pos, @Nullable Direction side, IEnergyStorage storage,
                                  TowerEnergyDirection direction) {

    /**
     * Creates an endpoint using the storage's current public permissions.
     *
     * <p>
     * This constructor is intended for fixed test and integration endpoints. Runtime tower discovery supplies the
     * tick-scoped direction explicitly.
     * </p>
     *
     * @param pos     block position that owns the energy storage
     * @param side    queried side, or null for internal access
     * @param storage resolved energy storage instance
     */
    public TowerEnergyEndpoint(BlockPos pos, @Nullable Direction side, IEnergyStorage storage) {
        this(pos, side, storage,
                TowerEnergyDirection.fromPermissions(storage.canExtract(), storage.canReceive()));
        if (this.direction == null) {
            throw new IllegalArgumentException("Tower energy endpoint must allow extraction or insertion");
        }
    }
}
