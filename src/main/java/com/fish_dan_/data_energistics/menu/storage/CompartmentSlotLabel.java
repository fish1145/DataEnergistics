package com.fish_dan_.data_energistics.menu.storage;

/**
 * Marker for optional compartment slots that need a semantic background drawn over their AE2 slot background.
 */
public interface CompartmentSlotLabel {

    /**
     * Index of the source column in the static F/K texture.
     *
     * @return zero for the fluid column, one for the wrapped-key column
     */
    int slotTextureColumn();
}
