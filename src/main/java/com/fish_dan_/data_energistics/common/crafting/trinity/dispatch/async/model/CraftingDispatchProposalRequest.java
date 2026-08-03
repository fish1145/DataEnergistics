package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;

import java.math.BigInteger;
import java.util.List;

/**
 * Immutable pure-planning input captured by the server thread for one worker dispatch opportunity.
 *
 * @param lease           complete worker and topology generation token
 * @param candidates      immutable provider-capacity observations in stable publication order
 * @param remainingCrafts positive logical work currently offerable by the server-thread transaction boundary
 * @param cursor          stable fairness cursor into {@code candidates}
 */
public record CraftingDispatchProposalRequest(
                                              CraftingDispatchLease lease,
                                              List<ProviderCapacitySnapshot> candidates,
                                              BigInteger remainingCrafts,
                                              int cursor) {

    private static final BigInteger MAXIMUM_LOGICAL_CRAFTS = BigInteger.valueOf(Long.MAX_VALUE);

    public CraftingDispatchProposalRequest {
        if (lease == null) {
            throw new IllegalArgumentException("Crafting dispatch proposal lease must not be null");
        }
        candidates = List.copyOf(candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Crafting dispatch proposal requires at least one candidate");
        }
        if (remainingCrafts == null || remainingCrafts.signum() <= 0 ||
                remainingCrafts.compareTo(MAXIMUM_LOGICAL_CRAFTS) > 0) {
            throw new IllegalArgumentException("Crafting dispatch proposal work must be in the positive long domain");
        }
        if (cursor < 0) {
            throw new IllegalArgumentException("Crafting dispatch proposal cursor must not be negative");
        }
        long publicationScope = candidates.getFirst().providerId().publicationScope();
        if (publicationScope != lease.gridGeneration()) {
            throw new IllegalArgumentException("Crafting dispatch proposal grid generation disagrees with its candidates");
        }
        for (ProviderCapacitySnapshot candidate : candidates) {
            if (candidate.providerId().publicationScope() != publicationScope) {
                throw new IllegalArgumentException("Crafting dispatch proposal candidates must belong to one grid");
            }
        }
    }
}
