package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model;

import org.jspecify.annotations.Nullable;

/**
 * One explicit preparation rejection and its optional provider-local target.
 *
 * @param status rejection status observed before a physical submission
 * @param target exact target to cache, or {@code null} when the status applies to the provider or pattern
 */
public record CraftingDispatchRejection(CraftingDispatchStatus status,
                                        @Nullable CraftingDispatchTarget target) {

    public CraftingDispatchRejection {
        if (status == null) {
            throw new IllegalArgumentException("Crafting dispatch rejection status must not be null");
        }
        if (status == CraftingDispatchStatus.ACCEPTED ||
                status == CraftingDispatchStatus.STALE ||
                status == CraftingDispatchStatus.BUDGET_EXHAUSTED ||
                status == CraftingDispatchStatus.FAILED_AFTER_OWNERSHIP) {
            throw new IllegalArgumentException("Crafting dispatch status is not a preparation rejection: " + status);
        }
    }

    /**
     * Creates a rejection applying to the provider or complete provider-pattern pair.
     *
     * @param status rejection status
     * @return provider or pattern scoped rejection
     */
    public static CraftingDispatchRejection scoped(CraftingDispatchStatus status) {
        return new CraftingDispatchRejection(status, null);
    }

    /**
     * Creates a rejection isolated to one provider-local target.
     *
     * @param status rejection status
     * @param target exact target identity
     * @return target-scoped rejection
     */
    public static CraftingDispatchRejection targeted(
                                                     CraftingDispatchStatus status,
                                                     CraftingDispatchTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("Crafting dispatch rejection target must not be null");
        }
        return new CraftingDispatchRejection(status, target);
    }
}
