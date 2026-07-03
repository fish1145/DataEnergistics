package com.fish_dan_.data_energistics.client.screen;

import appeng.client.gui.style.ScreenStyle;

/**
 * Applies Data Energistics slot layout additions to an AE2 screen style.
 * <p>
 * The interface exists so external AE screen layouts can keep loading from their owning mod while this mod contributes
 * only the slot style needed by its injected menu slots.
 */
public interface ScreenSlotStylePatch {

    /**
     * Adds missing slot style entries to the supplied screen style.
     *
     * @param style style document loaded by AE2 for the screen being opened
     */
    void apply(ScreenStyle style);
}
