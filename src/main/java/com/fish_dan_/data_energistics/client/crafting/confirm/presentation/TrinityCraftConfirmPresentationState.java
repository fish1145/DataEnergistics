package com.fish_dan_.data_energistics.client.crafting.confirm.presentation;

import appeng.api.stacks.AEKey;

/**
 * Screen-local tooltip page for one hovered material in the active crafting confirmation revision.
 *
 * <p>
 * The state is client-only, is never synchronized to the server, and is discarded with the screen instance. Consumers
 * must treat zero as “the material has no selected proved cycle”.
 * </p>
 */
public interface TrinityCraftConfirmPresentationState {

    /**
     * @param key hovered material whose related cycles provide the tooltip pages
     * @return one-based global cycle ordinal selected for this material, or zero when it has no proved cycle
     */
    int data_energistics$selectedCycleOrdinal(AEKey key);
}
