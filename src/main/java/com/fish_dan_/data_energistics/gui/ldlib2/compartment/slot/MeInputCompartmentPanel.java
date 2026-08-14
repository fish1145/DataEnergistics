package com.fish_dan_.data_energistics.gui.ldlib2.compartment.slot;

import com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge.AeMenuBridge;
import com.fish_dan_.data_energistics.menu.CompartmentMenu;

import appeng.menu.SlotSemantic;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import java.util.List;

/**
 * Creates the paired 5 by 5 configuration and buffer grids for an ME input compartment.
 */
public final class MeInputCompartmentPanel {

    public static final String PANEL_ID = "me_input_compartment_pairs";
    public static final String CONFIG_PANEL_ID = "me_input_compartment_config";
    public static final String BUFFER_PANEL_ID = "me_input_compartment_buffer";
    static final String CONFIG_SLOT_ID_PREFIX = "me_input_compartment_config_slot_";
    static final String BUFFER_SLOT_ID_PREFIX = "me_input_compartment_buffer_slot_";
    private static final int COLUMN_COUNT = CompartmentMenu.ME_COMPOSITE_INPUT_ROW_SLOT_COUNT;
    private static final int CONFIG_FIRST_MENU_INDEX = 0;
    private static final int BUFFER_FIRST_MENU_INDEX = 1;
    private static final int MENU_INDEX_STRIDE = 2;
    private static final int CONFIG_SLOT_LEFT = 8;
    private static final int BUFFER_SLOT_LEFT = 112;
    private static final int SLOT_TOP = 21;
    private static final int PANEL_WIDTH = 208;
    private static final int PANEL_HEIGHT = 112;
    private static final List<SlotSemantic> CONFIG_ROW_SEMANTICS = List.of(
            CompartmentMenu.COMPARTMENT_CONFIG_ROW_1,
            CompartmentMenu.COMPARTMENT_CONFIG_ROW_2,
            CompartmentMenu.COMPARTMENT_CONFIG_ROW_3,
            CompartmentMenu.COMPARTMENT_CONFIG_ROW_4,
            CompartmentMenu.COMPARTMENT_CONFIG_ROW_5);
    private static final List<SlotSemantic> BUFFER_ROW_SEMANTICS = List.of(
            CompartmentMenu.COMPARTMENT_BUFFER_ROW_1,
            CompartmentMenu.COMPARTMENT_BUFFER_ROW_2,
            CompartmentMenu.COMPARTMENT_BUFFER_ROW_3,
            CompartmentMenu.COMPARTMENT_BUFFER_ROW_4,
            CompartmentMenu.COMPARTMENT_BUFFER_ROW_5);

    private MeInputCompartmentPanel() {}

    /**
     * Wraps the exact interleaved slots created by {@link CompartmentMenu} as two aligned visual grids.
     *
     * @param menu   compartment menu whose first 50 slots are 25 configuration-buffer pairs
     * @param bridge existing-slot bridge owned by the current menu construction
     * @return a fresh paired-grid panel containing all 50 machine slots
     */
    public static UIElement create(CompartmentMenu menu, AeMenuBridge bridge) {
        UIElement panel = new UIElement();
        panel.setId(PANEL_ID);
        panel.layout(layout -> layout.width(PANEL_WIDTH).height(PANEL_HEIGHT));
        panel.addChild(CompartmentSlotPanel.createStridedRowGrid(
                menu,
                bridge,
                CONFIG_ROW_SEMANTICS,
                CONFIG_FIRST_MENU_INDEX,
                MENU_INDEX_STRIDE,
                COLUMN_COUNT,
                CONFIG_SLOT_LEFT,
                SLOT_TOP,
                CONFIG_PANEL_ID,
                CONFIG_SLOT_ID_PREFIX));
        panel.addChild(CompartmentSlotPanel.createStridedRowGrid(
                menu,
                bridge,
                BUFFER_ROW_SEMANTICS,
                BUFFER_FIRST_MENU_INDEX,
                MENU_INDEX_STRIDE,
                COLUMN_COUNT,
                BUFFER_SLOT_LEFT,
                SLOT_TOP,
                BUFFER_PANEL_ID,
                BUFFER_SLOT_ID_PREFIX));
        return panel;
    }
}
