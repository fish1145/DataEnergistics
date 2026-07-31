package com.fish_dan_.data_energistics.menu.crafting;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;

import net.minecraft.network.chat.Component;

/**
 * Synchronized Trinity planning metadata shared by the confirmation menu and its client screen.
 */
public interface TrinityCraftConfirmMenuState {

    /**
     * @return quantity mode retained for initial planning, replan, and returning to the amount page
     */
    CraftingQuantityMode data_energistics$quantityMode();

    /**
     * @param quantityMode player selection transferred from the amount menu
     */
    void data_energistics$setQuantityMode(CraftingQuantityMode quantityMode);

    /**
     * @return whether the executable result can run only on Trinity CPUs
     */
    boolean data_energistics$isTrinityOnly();

    /**
     * @return whether expected materials may change through a legal dynamic variant during execution
     */
    boolean data_energistics$hasDynamicMaterialWarning();

    /**
     * @return whether a Trinity diagnostic accompanies the displayed plan
     */
    boolean data_energistics$hasDiagnostic();

    /**
     * @return synchronized player-facing diagnostic
     */
    Component data_energistics$diagnostic();
}
