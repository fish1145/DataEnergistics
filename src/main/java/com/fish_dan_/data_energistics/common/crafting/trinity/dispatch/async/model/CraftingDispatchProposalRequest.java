package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.ProviderCapacityCapture;

import java.math.BigInteger;

/**
 * Immutable pure-planning input captured by the server thread for one worker dispatch opportunity.
 *
 * @param lease           complete worker and topology generation token
 * @param capacity        immutable provider-capacity capture and its complete cache identity
 * @param remainingCrafts positive logical work currently offerable by the server-thread transaction boundary
 * @param cursor          stable provider and target fairness position
 */
public record CraftingDispatchProposalRequest(
                                               CraftingDispatchLease lease,
                                               ProviderCapacityCapture capacity,
                                               BigInteger remainingCrafts,
                                               CraftingDispatchCursor cursor) {

    private static final BigInteger MAXIMUM_LOGICAL_CRAFTS = BigInteger.valueOf(Long.MAX_VALUE);

    public CraftingDispatchProposalRequest {
        if (lease == null) {
            throw new IllegalArgumentException("Crafting dispatch proposal lease must not be null");
        }
        if (capacity == null || capacity.snapshots().isEmpty()) {
            throw new IllegalArgumentException("Crafting dispatch proposal requires at least one candidate");
        }
        if (remainingCrafts == null || remainingCrafts.signum() <= 0 ||
                remainingCrafts.compareTo(MAXIMUM_LOGICAL_CRAFTS) > 0) {
            throw new IllegalArgumentException("Crafting dispatch proposal work must be in the positive long domain");
        }
        if (cursor == null) {
            throw new IllegalArgumentException("Crafting dispatch proposal cursor must not be null");
        }
        long publicationScope = capacity.key().gridScope();
        if (publicationScope != lease.gridGeneration()) {
            throw new IllegalArgumentException("Crafting dispatch proposal grid generation disagrees with its candidates");
        }
        for (var candidate : capacity.snapshots()) {
            if (candidate.providerId().publicationScope() != publicationScope) {
                throw new IllegalArgumentException("Crafting dispatch proposal candidates must belong to one grid");
            }
        }
    }
}
