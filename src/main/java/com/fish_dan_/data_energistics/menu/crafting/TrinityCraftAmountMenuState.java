package com.fish_dan_.data_energistics.menu.crafting;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;

/**
 * Mixin bridge exposing the synchronized quantity selection owned by AE2's Craft Amount menu.
 */
public interface TrinityCraftAmountMenuState {

    /**
     * @return currently synchronized player selection
     */
    CraftingQuantityMode data_energistics$quantityMode();

    /**
     * Applies a player selection locally and sends the existing AE2 menu client action when needed.
     *
     * @param quantityMode new selection
     */
    void data_energistics$setQuantityMode(CraftingQuantityMode quantityMode);
}
