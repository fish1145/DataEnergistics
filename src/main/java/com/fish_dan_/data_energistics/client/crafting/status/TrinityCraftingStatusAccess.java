package com.fish_dan_.data_energistics.client.crafting.status;

/**
 * Client-thread bridge to the selected CPU screen's exact status state. It keeps packet assembly owned by the screen,
 * so closing a menu cannot retain quantities or serials globally. This is internal integration, not a provider API.
 */
public interface TrinityCraftingStatusAccess {

    /**
     * Returns the non-null state for this screen's lifetime; callers must verify the active container before delivery.
     */
    TrinityCraftingStatusState data_energistics$craftingStatusState();
}
