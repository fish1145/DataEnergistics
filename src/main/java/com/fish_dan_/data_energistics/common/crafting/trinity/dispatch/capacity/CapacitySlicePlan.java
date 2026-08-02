package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import java.util.List;

/**
 * Immutable startup-first allocation and the cursor to use for the next planning pass.
 *
 * @param slices     allocations in the rotated target order, bounded by the physical call limit
 * @param nextCursor index in the original snapshot list where the next pass starts
 */
public record CapacitySlicePlan(List<CapacitySlice> slices, int nextCursor) {

    public CapacitySlicePlan {
        slices = List.copyOf(slices);
        if (nextCursor < 0) {
            throw new IllegalArgumentException("Capacity slice cursor must not be negative");
        }
    }
}
