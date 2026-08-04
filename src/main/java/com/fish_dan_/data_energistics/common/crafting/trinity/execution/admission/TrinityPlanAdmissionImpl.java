package com.fish_dan_.data_energistics.common.crafting.trinity.execution.admission;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.crafting.CraftingPlan;

/**
 * Conservative allowlist that accepts AE2's native value plan and explicit Trinity-compatible plans only.
 */
final class TrinityPlanAdmissionImpl implements TrinityPlanAdmission {

    @Override
    public Decision decide(ICraftingPlan plan, Route route) {
        if (plan instanceof CraftingPlan || plan instanceof TrinityCpuExecutablePlan) {
            return Decision.SUBMIT_TO_TRINITY;
        }
        return switch (route) {
            case AUTOMATIC_SELECTION, FALLBACK -> Decision.DEFER_TO_ORIGINAL;
            case EXPLICIT_TARGET, DIRECT_CPU -> Decision.REJECT_TRINITY;
        };
    }

    @Override
    public boolean isCompatibleWith(ICraftingPlan plan, CpuFamily cpuFamily) {
        return switch (cpuFamily) {
            case TRINITY -> decide(plan, Route.AUTOMATIC_SELECTION) == Decision.SUBMIT_TO_TRINITY;
            case NON_TRINITY -> !(plan instanceof TrinityCpuExecutablePlan);
        };
    }
}
