package com.fish_dan_.data_energistics.gui.ldlib2.trinity.pattern.physical;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.bridge.AeMenuBridge;
import com.fish_dan_.data_energistics.gui.ldlib2.ae.inventory.AePlayerInventoryPanel;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.layout.TrinityUiXmlLayouts;
import com.fish_dan_.data_energistics.menu.trinity.TrinityPatternCoreMenu;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

/** Builds and mounts the complete LDLib2 surface for one physical Trinity pattern core. */
public final class TrinityPatternCoreUi {

    /** Stable root identifier used by integration tests and future host composition. */
    public static final String ROOT_ID = "trinity_pattern_core_root";
    /** Stable identifier of the panel that owns the 64 page slots and its controls. */
    public static final String PANEL_ID = "trinity_pattern_core_panel";
    /** Stable identifier of the eight-by-eight pattern slot grid. */
    public static final String PATTERN_GRID_ID = "trinity_pattern_core_pattern_grid";
    /** Prefix followed by the zero-based page-local index of each pattern wrapper. */
    public static final String PATTERN_SLOT_ID_PREFIX = "trinity_pattern_core_pattern_";
    /** Stable identifier of the previous-page button. */
    public static final String PREVIOUS_PAGE_ID = "trinity_pattern_core_previous_page";
    /** Stable identifier of the current page label. */
    public static final String PAGE_INFO_ID = "trinity_pattern_core_page_info";
    /** Stable identifier of the next-page button. */
    public static final String NEXT_PAGE_ID = "trinity_pattern_core_next_page";
    /** Stable identifier of the atomic refund button. */
    public static final String REFUND_ALL_ID = "trinity_pattern_core_refund_all";

    private TrinityPatternCoreUi() {}

    /**
     * Wraps all existing AE2 slots and mounts an isomorphic ModularUI during menu construction on both sides.
     *
     * @param menu  menu that already owns the 64 page proxies and 36 player slots
     * @param title physical core title rendered by the LDLib2 tree
     * @return the ModularUI attached to the menu
     */
    public static ModularUI mount(TrinityPatternCoreMenu menu, Component title) {
        if (menu == null || title == null) {
            Data_Energistics.LOGGER.error("Cannot mount the Trinity Pattern Core LDLib2 UI without its menu and title");
            throw new IllegalArgumentException("Trinity Pattern Core menu and title must be present");
        }

        try {
            AeMenuBridge bridge = AeMenuBridge.create(menu);
            UI ui = TrinityUiXmlLayouts.load("pattern_core");
            UIElement root = ui.rootElement;
            root.addChild(TrinityPatternCorePanel.create(menu, bridge, title));
            root.addChild(AePlayerInventoryPanel.createFlow(
                    menu,
                    bridge,
                    "trinity-pattern-player-inventory",
                    "trinity-pattern-player-inventory-grid",
                    "trinity-pattern-player-hotbar-grid",
                    "trinity-pattern-player-inventory-slot"));

            ModularUI modularUI = ModularUI.of(ui, menu.getPlayer());
            bridge.mount(modularUI);
            return modularUI;
        } catch (RuntimeException | Error failure) {
            Data_Energistics.LOGGER.error("Failed to create the Trinity Pattern Core LDLib2 UI", failure);
            throw failure;
        }
    }
}
