package com.fish_dan_.data_energistics.menu;

/**
 * Marker for optional compartment slots that need a semantic background drawn over their AE2 slot background.
 */
public interface CompartmentSlotLabel {

    /**
     * Index of the source row in the static F/K column texture.
     *
     * @return zero for the base fluid row, one for the base wrapped-key row
     */
    int slotTextureRow();
}
