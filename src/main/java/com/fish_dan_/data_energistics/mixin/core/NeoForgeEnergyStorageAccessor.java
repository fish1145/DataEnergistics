package com.fish_dan_.data_energistics.mixin.core;

import net.neoforged.neoforge.energy.EnergyStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the reference NeoForge energy storage state to the tower's unlimited energy adapter.
 *
 * <p>
 * The accessor avoids reflective mutation for the most common FE implementation while leaving receive and extract
 * permission checks on the public {@link EnergyStorage} API.
 */
@Mixin(EnergyStorage.class)
public interface NeoForgeEnergyStorageAccessor {

    /**
     * Reads the current stored energy field.
     *
     * @return stored FE amount
     */
    @Accessor("energy")
    int dataEnergistics$getEnergy();

    /**
     * Updates the current stored energy field after validation by the unlimited adapter.
     *
     * @param energy new stored FE amount
     */
    @Accessor("energy")
    void dataEnergistics$setEnergy(int energy);

    /**
     * Reads the configured storage capacity field.
     *
     * @return maximum FE amount
     */
    @Accessor("capacity")
    int dataEnergistics$getCapacity();
}
