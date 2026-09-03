package com.fish_dan_.data_energistics.menu.crafting.tree.session;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.accessor.crafting.CraftingPlanTiming;
import com.fish_dan_.data_energistics.common.crafting.tree.model.CraftingPlanGraph;
import com.fish_dan_.data_energistics.common.crafting.tree.projection.CraftingPlanGraphProjection;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityDiagnosedCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.menu.crafting.projection.TrinityCraftingPlanSummaryProjection;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.TrinityCraftingCycleSummaryProjection;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleSummary;

import net.minecraft.network.chat.Component;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.menu.me.crafting.CraftingPlanSummary;
import org.jspecify.annotations.Nullable;

/** A complete authoritative outcome plus its optional, independently fallible visualization. */
public record CraftingPlanTreeResult(ICraftingPlan plan, CraftingPlanSummary summary,
                                     @Nullable TrinityCraftingCycleSummary cycles, long planningNanos,
                                     @Nullable CraftingPlanGraph graph, Component graphError) {

    /** Projects exactly once on the server thread. A drawing failure must not destroy a valid crafting plan. */
    public static CraftingPlanTreeResult create(ICraftingPlan plan, CraftingPlanTreeRequest request,
                                                IGrid grid, IActionSource source) {
        var available = grid.getStorageService().getInventory().getAvailableStacks();
        CraftingPlanSummary summary;
        TrinityCraftingCycleSummary cycles = null;
        long nanos = plan instanceof CraftingPlanTiming timing ? timing.dataEnergistics$calculationNanos() : 0L;
        if (plan instanceof TrinityCraftingPlan trinity) {
            summary = TrinityCraftingPlanSummaryProjection.create(trinity);
            cycles = TrinityCraftingCycleSummaryProjection.create(trinity, available);
            nanos = trinity.statistics().planningNanos();
        } else if (plan instanceof TrinityDiagnosedCraftingPlan diagnosed) {
            nanos = diagnosed.calculationNanos();
            if (diagnosed.ae2FallbackEstimate()) {
                summary = CraftingPlanSummary.fromJob(grid, source, diagnosed.delegate());
            } else {
                summary = TrinityCraftingPlanSummaryProjection.createDiagnostic(diagnosed);
                cycles = TrinityCraftingCycleSummaryProjection.create(diagnosed, available);
            }
        } else {
            summary = CraftingPlanSummary.fromJob(grid, source, plan);
        }
        CraftingPlanGraph graph = null;
        Component error = Component.empty();
        try {
            graph = CraftingPlanGraphProjection.create(plan, request.quantityMode(), available, nanos);
        } catch (RuntimeException failure) {
            Data_Energistics.LOGGER.error("Cannot project crafting plan tree for player={} target={}",
                    request.playerId(), request.target(), failure);
            error = Component.translatable("gui.data_energistics.plan_tree.graph_failed");
        }
        return new CraftingPlanTreeResult(plan, summary, cycles, nanos, graph, error);
    }
}
