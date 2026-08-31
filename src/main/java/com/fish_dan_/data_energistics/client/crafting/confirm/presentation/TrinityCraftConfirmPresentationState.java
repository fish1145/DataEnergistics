package com.fish_dan_.data_energistics.client.crafting.confirm.presentation;

/**
 * Screen-local selection of one proved Trinity cycle in the active crafting confirmation revision.
 *
 * <p>
 * The state is client-only, is never synchronized to the server, and is discarded with the screen instance. Consumers
 * must treat zero as “no proved cycle available”.
 * </p>
 */
public interface TrinityCraftConfirmPresentationState {

    /**
     * @return one-based selected cycle ordinal, or zero when the current revision contains no proved cycle
     */
    int data_energistics$selectedCycleOrdinal();
}
