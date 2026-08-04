package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;

/**
 * One immutable physical-call allocation for an exact provider capacity snapshot.
 *
 * @param target        exact target observation selected by the planner
 * @param logicalCrafts positive logical craft count assigned to one physical call
 */
public record CapacitySlice(ProviderCapacitySnapshot target, long logicalCrafts) {

    public CapacitySlice {
        if (target == null) {
            throw new IllegalArgumentException("Capacity slice target must not be null");
        }
        if (logicalCrafts <= 0L) {
            throw new IllegalArgumentException("Capacity slice logical craft count must be positive");
        }
    }
}
