package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget.CraftingDispatchLimits;

/**
 * Per-grid Governor policy captured with one proposal admission attempt.
 *
 * @param actorPermits      maximum outstanding proposals for the grid
 * @param providerQuantum   maximum outstanding proposals reserving one provider
 * @param proposalHighWater global executor queue depth that defers this grid
 * @param enabled           whether asynchronous proposals may be admitted
 */
public record DispatchProposalPolicy(
                                     int actorPermits,
                                     int providerQuantum,
                                     int proposalHighWater,
                                     boolean enabled) {

    public DispatchProposalPolicy {
        if (actorPermits <= 0 || providerQuantum <= 0 || proposalHighWater <= 0) {
            throw new IllegalArgumentException("Dispatch proposal policy limits must be positive");
        }
    }

    /**
     * @return deterministic pre-Governor hard proposal policy retained for compatible callers
     */
    public static DispatchProposalPolicy defaults() {
        return new DispatchProposalPolicy(
                DispatchProposalLimits.DEFAULT_PER_GRID_OUTSTANDING,
                CraftingDispatchLimits.DEFAULT_MAX_ATTEMPTS_PER_PROVIDER,
                DispatchProposalLimits.DEFAULT_QUEUE_CAPACITY,
                true);
    }
}
