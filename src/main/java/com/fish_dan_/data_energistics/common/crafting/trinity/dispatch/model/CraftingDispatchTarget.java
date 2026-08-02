package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model;

/**
 * Immutable provider-local target identity used only for dispatch ordering and negative-cache isolation.
 *
 * @param stableIdentity stable identity within one provider, such as a side or published route
 */
public record CraftingDispatchTarget(String stableIdentity) {

    private static final CraftingDispatchTarget PROVIDER = new CraftingDispatchTarget("provider");

    public CraftingDispatchTarget {
        if (stableIdentity == null || stableIdentity.isBlank()) {
            throw new IllegalArgumentException("Crafting dispatch target identity must not be blank");
        }
    }

    /**
     * Returns the conservative target used by providers without an exposed routing identity.
     *
     * @return provider-scoped fallback target
     */
    public static CraftingDispatchTarget provider() {
        return PROVIDER;
    }
}
