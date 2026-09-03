package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.ProviderCapacityCapture;

import java.math.BigInteger;
import java.util.Set;

/**
 * Immutable pure-planning input captured by the server thread for one worker dispatch opportunity.
 *
 * @param lease           complete worker and topology generation token
 * @param capacity        immutable provider-capacity capture and its complete cache identity
 * @param remainingCrafts positive logical work currently offerable by the server-thread transaction boundary
 * @param cursor          stable provider and target fairness position
 * @param exclusions      accumulated transient provider/target failures that replacement proposals must retain
 */
public record CraftingDispatchProposalRequest(
                                              CraftingDispatchLease lease,
                                              ProviderCapacityCapture capacity,
                                              BigInteger remainingCrafts,
                                              CraftingDispatchCursor cursor,
                                              Set<CraftingDispatchExclusion> exclusions) {

    /**
     * Creates an initial proposal request without replacement history.
     */
    public CraftingDispatchProposalRequest(
                                           CraftingDispatchLease lease,
                                           ProviderCapacityCapture capacity,
                                           BigInteger remainingCrafts,
                                           CraftingDispatchCursor cursor) {
        this(lease, capacity, remainingCrafts, cursor, Set.of());
    }

    public CraftingDispatchProposalRequest {
        if (capacity.snapshots().isEmpty()) {
            throw new IllegalArgumentException("Crafting dispatch proposal requires at least one candidate");
        }
        if (remainingCrafts.signum() <= 0) {
            throw new IllegalArgumentException("Crafting dispatch proposal work must be positive");
        }
        exclusions = Set.copyOf(exclusions);
        long publicationScope = capacity.key().gridScope();
        if (publicationScope != lease.gridGeneration()) {
            throw new IllegalArgumentException("Crafting dispatch proposal grid generation disagrees with its candidates");
        }
        for (var candidate : capacity.snapshots()) {
            if (candidate.providerId().publicationScope() != publicationScope) {
                throw new IllegalArgumentException("Crafting dispatch proposal candidates must belong to one grid");
            }
            if (exclusions.stream().anyMatch(exclusion -> exclusion.excludes(candidate))) {
                throw new IllegalArgumentException("Crafting dispatch proposal retained an excluded candidate");
            }
        }
    }
}
