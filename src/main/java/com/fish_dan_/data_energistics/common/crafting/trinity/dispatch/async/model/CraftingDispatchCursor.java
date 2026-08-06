package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model;

/**
 * Persistent two-level fairness position that rotates providers before targets owned by each provider.
 *
 * @param provider non-negative cursor into stable provider publication order
 * @param target   non-negative target round advanced whenever the provider cursor wraps
 */
public record CraftingDispatchCursor(int provider, long target) {

    private static final CraftingDispatchCursor INITIAL = new CraftingDispatchCursor(0, 0);

    public CraftingDispatchCursor {
        if (provider < 0 || target < 0) {
            throw new IllegalArgumentException("Crafting dispatch cursors must not be negative");
        }
    }

    /** @return initial provider and target fairness position */
    public static CraftingDispatchCursor initial() {
        return INITIAL;
    }
}
