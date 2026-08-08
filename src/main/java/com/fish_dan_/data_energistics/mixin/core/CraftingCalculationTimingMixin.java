package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.accessor.CraftingPlanTiming;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.crafting.CraftingCalculation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Measures the complete native AE2 calculation, including its intentional cross-tick pauses.
 */
@Mixin(CraftingCalculation.class)
public abstract class CraftingCalculationTimingMixin {

    @WrapMethod(method = "run")
    private ICraftingPlan dataEnergistics$measureCalculation(Operation<ICraftingPlan> original) {
        long startedNanos = System.nanoTime();
        ICraftingPlan plan = original.call();
        ((CraftingPlanTiming.Mutable) plan).dataEnergistics$setCalculationNanos(System.nanoTime() - startedNanos);
        return plan;
    }
}
