package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider;

/**
 * Narrow common-layer bridge from a crafting service to its identity-preserving provider publication index.
 *
 * <p>
 * Dispatch code depends on this contract instead of AE2 private fields or Mixin implementation classes.
 * </p>
 */
public interface CraftingProviderPublicationAccess {

    /**
     * Returns the grid-local publication index owned by the current server-thread crafting service.
     *
     * @return read-only publication index
     */
    CraftingProviderPublicationIndex data_energistics$craftingProviderPublicationIndex();
}
