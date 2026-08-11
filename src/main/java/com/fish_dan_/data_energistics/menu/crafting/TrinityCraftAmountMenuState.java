package com.fish_dan_.data_energistics.menu.crafting;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;

/**
 * Mixin bridge exposing the synchronized quantity selection owned by AE2's Craft Amount menu.
 */
public interface TrinityCraftAmountMenuState {

    /**
     * Exact confirmation payload carried by AE2's typed menu-action channel.
     *
     * @param amount             requested amount in AE2's native signed-long range
     * @param craftMissingAmount whether the amount field used AE2's equals prefix
     * @param autoStart          whether the completed plan should start immediately
     */
    record Confirmation(long amount, boolean craftMissingAmount, boolean autoStart) {}

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

    /**
     * Confirms one exact long-sized request. Client calls are forwarded through the current menu action.
     *
     * @param amount             positive requested amount
     * @param craftMissingAmount whether the amount field used AE2's equals prefix
     * @param autoStart          whether the completed plan should start immediately
     */
    void data_energistics$confirm(long amount, boolean craftMissingAmount, boolean autoStart);

    /**
     * @return exact amount restored when the amount screen is reopened
     */
    long data_energistics$initialAmount();

    /**
     * Retains the exact amount while moving back from the confirmation menu.
     *
     * @param amount positive amount to restore
     */
    void data_energistics$setInitialAmount(long amount);
}
