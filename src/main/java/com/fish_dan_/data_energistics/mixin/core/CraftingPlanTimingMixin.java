package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.accessor.CraftingPlanTiming;

import appeng.crafting.CraftingPlan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Keeps AE2 calculation timing on the exact plan instance returned by its calculation future.
 */
@Mixin(CraftingPlan.class)
public abstract class CraftingPlanTimingMixin implements CraftingPlanTiming.Mutable {

    @Unique
    private long dataEnergistics$calculationNanos;

    @Override
    public long dataEnergistics$calculationNanos() {
        return this.dataEnergistics$calculationNanos;
    }

    @Override
    public void dataEnergistics$setCalculationNanos(long calculationNanos) {
        this.dataEnergistics$calculationNanos = calculationNanos;
    }
}
