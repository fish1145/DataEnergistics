package com.fish_dan_.data_energistics.menu.patternencoding;

/**
 * Exposes the current encoded processing pattern's first-output matching rule to its amount sub-screen.
 */
public interface PatternOutputMatchMenu {

    /**
     * Returns whether the selected processing output accepts the same registered item regardless of components.
     *
     * @return current per-pattern output matching choice
     */
    boolean data_energistics$isProcessingOutputSameItem();

    /**
     * Updates the per-pattern output matching choice and sends the client action when called client-side.
     *
     * @param enabled true for SAME_ITEM, false for complete AEItemKey matching
     */
    void data_energistics$setProcessingOutputSameItem(boolean enabled);
}
