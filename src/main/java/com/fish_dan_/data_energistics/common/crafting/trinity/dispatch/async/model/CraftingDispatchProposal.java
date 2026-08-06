package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;

/**
 * Immutable background result naming one physical provider call without carrying mutable server state.
 *
 * @param lease         exact worker generation token copied from the request
 * @param target        selected immutable provider-capacity observation
 * @param logicalCrafts positive logical batch proposed for the physical call
 * @param nextCursor    provider and target cursor to adopt only after a real physical call
 */
public record CraftingDispatchProposal(
                                       CraftingDispatchLease lease,
                                       ProviderCapacitySnapshot target,
                                       long logicalCrafts,
                                       CraftingDispatchCursor nextCursor) {

    public CraftingDispatchProposal {
        if (lease == null) {
            throw new IllegalArgumentException("Crafting dispatch proposal lease must not be null");
        }
        if (target == null) {
            throw new IllegalArgumentException("Crafting dispatch proposal target must not be null");
        }
        if (target.providerId().publicationScope() != lease.gridGeneration()) {
            throw new IllegalArgumentException("Crafting dispatch proposal target belongs to another grid generation");
        }
        if (logicalCrafts <= 0L) {
            throw new IllegalArgumentException("Crafting dispatch proposal count must be positive");
        }
        if (nextCursor == null) {
            throw new IllegalArgumentException("Crafting dispatch proposal cursor must not be null");
        }
    }
}
