package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;

import java.util.Set;

/**
 * Immutable background result naming one physical provider call without carrying mutable server state.
 *
 * @param lease         exact worker generation token copied from the request
 * @param target        selected immutable provider-capacity observation
 * @param logicalCrafts positive logical batch proposed for the physical call
 * @param nextCursor    provider and target cursor to adopt only after a real physical call
 * @param exclusions    accumulated transient replacement failures carried to server-thread revalidation
 */
public record CraftingDispatchProposal(
                                       CraftingDispatchLease lease,
                                       ProviderCapacitySnapshot target,
                                       long logicalCrafts,
                                       CraftingDispatchCursor nextCursor,
                                       Set<CraftingDispatchExclusion> exclusions) {

    /**
     * Creates an initial proposal without replacement history.
     */
    public CraftingDispatchProposal(
                                    CraftingDispatchLease lease,
                                    ProviderCapacitySnapshot target,
                                    long logicalCrafts,
                                    CraftingDispatchCursor nextCursor) {
        this(lease, target, logicalCrafts, nextCursor, Set.of());
    }

    public CraftingDispatchProposal {
        if (target.providerId().publicationScope() != lease.gridGeneration()) {
            throw new IllegalArgumentException("Crafting dispatch proposal target belongs to another grid generation");
        }
        if (logicalCrafts <= 0L) {
            throw new IllegalArgumentException("Crafting dispatch proposal count must be positive");
        }
        exclusions = Set.copyOf(exclusions);
        if (exclusions.stream().anyMatch(exclusion -> exclusion.excludes(target))) {
            throw new IllegalArgumentException("Crafting dispatch proposal selected an excluded target");
        }
    }
}
