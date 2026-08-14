package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalLimits;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalPolicy;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchLimits;

/**
 * Immutable physical-dispatch policy published by one per-grid Governor.
 *
 * @param dispatchLimits      server-thread physical call and time limits
 * @param actorPermits        maximum outstanding proposals admitted for the grid
 * @param providerQuantum     provider fairness quantum, never a logical counted-batch limit
 * @param proposalHighWater   global outstanding-proposal depth at which this grid defers new proposals
 * @param retryBackoffTicks   retry delay used for bounded scheduler pressure
 * @param asynchronousEnabled whether new asynchronous proposals may be admitted
 */
public record CraftingDispatchBudget(
                                     CraftingDispatchLimits dispatchLimits,
                                     int actorPermits,
                                     int providerQuantum,
                                     int proposalHighWater,
                                     int retryBackoffTicks,
                                     boolean asynchronousEnabled) {

    private static final CraftingDispatchBudget LEGACY_FIXED_HARD = new CraftingDispatchBudget(
            CraftingDispatchLimits.DEFAULT,
            actorPermitsFor(CraftingDispatchLimits.DEFAULT_MAX_ATTEMPTS_PER_GRID),
            CraftingDispatchLimits.DEFAULT_MAX_ATTEMPTS_PER_PROVIDER,
            DispatchProposalLimits.DEFAULT_MAX_OUTSTANDING,
            1,
            true);

    public CraftingDispatchBudget {
        if (dispatchLimits == null) {
            throw new IllegalArgumentException("Crafting dispatch limits are required");
        }
        if (actorPermits <= 0 || providerQuantum <= 0 || proposalHighWater <= 0 || retryBackoffTicks <= 0) {
            throw new IllegalArgumentException("Crafting dispatch runtime budgets must be positive");
        }
        if (providerQuantum > dispatchLimits.maxAttemptsPerProvider()) {
            throw new IllegalArgumentException("Provider quantum must not exceed the physical provider-call limit");
        }
    }

    /**
     * @return scheduler policy retaining only proposal-admission controls
     */
    public DispatchProposalPolicy proposalPolicy() {
        return new DispatchProposalPolicy(
                this.actorPermits,
                this.providerQuantum,
                this.proposalHighWater,
                this.asynchronousEnabled);
    }

    /**
     * Scales one grid's outstanding asynchronous work with its current physical-attempt window.
     *
     * @param maxAttemptsPerGrid current physical-attempt ceiling
     * @return bounded outstanding proposal permits
     */
    public static int actorPermitsFor(int maxAttemptsPerGrid) {
        if (maxAttemptsPerGrid <= 0) {
            throw new IllegalArgumentException("Grid crafting dispatch limit must be positive");
        }
        int permits = Math.floorDiv(maxAttemptsPerGrid - 1, 256) + 1;
        return Math.min(DispatchProposalLimits.DEFAULT_PER_GRID_OUTSTANDING, permits);
    }

    /**
     * @return deterministic pre-Governor hard policy used only by the retained three-argument execution overloads
     */
    public static CraftingDispatchBudget legacyFixedHard() {
        return LEGACY_FIXED_HARD;
    }
}
