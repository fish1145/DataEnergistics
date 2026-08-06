package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchCursor;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;

import java.util.List;

/**
 * Immutable linear proposal order calculated before reservation state is consulted.
 *
 * @param candidates provider-first candidates that may be reserved in one linear pass
 */
public record DispatchProposalCandidatePlan(List<Candidate> candidates) {

    public DispatchProposalCandidatePlan {
        candidates = List.copyOf(candidates);
    }

    /**
     * @param target        exact immutable provider observation
     * @param logicalCrafts maximum logical count suggested by pure captured facts
     * @param nextCursor    cursor committed only after a real server-thread provider call
     */
    public record Candidate(
                            ProviderCapacitySnapshot target,
                            long logicalCrafts,
                            CraftingDispatchCursor nextCursor) {

        public Candidate {
            if (target == null || nextCursor == null) {
                throw new IllegalArgumentException("Dispatch proposal candidate must be complete");
            }
            if (logicalCrafts <= 0L) {
                throw new IllegalArgumentException("Dispatch proposal candidate count must be positive");
            }
        }
    }
}
