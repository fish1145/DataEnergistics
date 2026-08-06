package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchCursor;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;

import java.util.List;

/**
 * Immutable provider-first allocations whose successor cursors remain suggestions until a physical call occurs.
 *
 * @param slices fair positive allocations in physical-call order
 */
public record DispatchCapacitySlicePlan(List<Slice> slices) {

    public DispatchCapacitySlicePlan {
        slices = List.copyOf(slices);
    }

    /**
     * @param target        exact immutable capacity observation
     * @param logicalCrafts positive logical allocation
     * @param nextCursor    cursor committed only after a real provider invocation
     */
    public record Slice(
                        ProviderCapacitySnapshot target,
                        long logicalCrafts,
                        CraftingDispatchCursor nextCursor) {

        public Slice {
            if (target == null || nextCursor == null) {
                throw new IllegalArgumentException("Dispatch capacity slice must be complete");
            }
            if (logicalCrafts <= 0L) {
                throw new IllegalArgumentException("Dispatch capacity slice count must be positive");
            }
        }
    }
}
