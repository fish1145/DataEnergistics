package com.fish_dan_.data_energistics.gui.ldlib2.compartment;

import com.fish_dan_.data_energistics.gui.ldlib2.AeMenuBridge;
import com.fish_dan_.data_energistics.menu.CompartmentMenu;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

/**
 * Creates the 9 by 4 read-only display surface for an ME output compartment.
 */
public final class MeOutputCompartmentPanel {

    public static final String PANEL_ID = "me_output_compartment_display";
    static final String DISPLAY_SLOT_ID_PREFIX = "me_output_compartment_slot_";
    private static final int FIRST_MENU_INDEX = 0;
    private static final int COLUMN_COUNT = 9;
    private static final int SLOT_LEFT = 8;
    private static final int SLOT_TOP = 21;

    private MeOutputCompartmentPanel() {}

    /**
     * Wraps the exact display slots created by {@link CompartmentMenu} without changing their read-only behavior.
     *
     * @param menu   compartment menu whose first 36 slots are the ME output display
     * @param bridge existing-slot bridge owned by the current menu construction
     * @return a fresh 36-slot display panel
     */
    public static UIElement create(CompartmentMenu menu, AeMenuBridge bridge) {
        return CompartmentSlotPanel.createContiguousGrid(
                menu,
                bridge,
                CompartmentMenu.COMPARTMENT_BUFFER,
                FIRST_MENU_INDEX,
                CompartmentMenu.ME_COMPOSITE_OUTPUT_DISPLAY_SLOT_COUNT,
                COLUMN_COUNT,
                SLOT_LEFT,
                SLOT_TOP,
                PANEL_ID,
                DISPLAY_SLOT_ID_PREFIX);
    }
}
