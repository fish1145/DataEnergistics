package com.fish_dan_.data_energistics.mixin.ae2ct;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityDiagnosedCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.integration.ae.ae2ct.TrinityCraftingTreeProjection;
import com.fish_dan_.data_energistics.menu.crafting.projection.TrinityCraftingPlanSummaryProjection;

import appeng.menu.me.crafting.CraftingPlanSummary;
import com.neuvillette.ae2ct.api.ICraftingPlanSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Initializes AE2 Crafting Tree's summary extension when Trinity bypasses AE2's concrete CraftingPlan projection.
 */
@Mixin(value = TrinityCraftingPlanSummaryProjection.class, remap = false)
public abstract class TrinityCraftingPlanSummaryProjectionMixin {

    @Inject(method = "create", at = @At("RETURN"))
    private static void dataEnergistics$attachCraftingTree(
                                                           TrinityCraftingPlan plan,
                                                           CallbackInfoReturnable<CraftingPlanSummary> cir) {
        ICraftingPlanSummary summary = (ICraftingPlanSummary) cir.getReturnValue();
        summary.setJob(TrinityCraftingTreeProjection.create(plan));
    }

    @Inject(method = "createDiagnostic", at = @At("RETURN"))
    private static void dataEnergistics$attachEmptyDiagnosticTree(
                                                                  TrinityDiagnosedCraftingPlan plan,
                                                                  CallbackInfoReturnable<CraftingPlanSummary> cir) {
        ICraftingPlanSummary summary = (ICraftingPlanSummary) cir.getReturnValue();
        summary.setJob(TrinityCraftingTreeProjection.createDiagnostic(plan.finalOutput()));
    }
}
