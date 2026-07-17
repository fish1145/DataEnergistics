package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.AeMenuBridge;
import com.fish_dan_.data_energistics.gui.ldlib2.AePlayerInventoryLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.AePlayerInventoryPanel;
import com.fish_dan_.data_energistics.menu.TrinityPatternCoreMenu;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
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

    private static final int WIDTH = 176;
    private static final int HEIGHT = 256;
    private static final AePlayerInventoryLayout PLAYER_INVENTORY_LAYOUT = new AePlayerInventoryLayout(8, 172, 230);

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
            UIElement root = new UIElement();
            root.setId(ROOT_ID);
            root.layout(layout -> layout.width(WIDTH).height(HEIGHT));
            root.style(style -> style.backgroundTexture(SpriteTexture
                    .of("ae2:textures/guis/me_digital_pattern_processing_core.png")
                    .setSprite(0, 0, WIDTH, HEIGHT)));
            root.addChild(TrinityPatternCorePanel.create(menu, bridge, title));
            root.addChild(AePlayerInventoryPanel.create(menu, bridge, PLAYER_INVENTORY_LAYOUT));

            ModularUI modularUI = ModularUI.of(UI.of(root), menu.getPlayer());
            bridge.mount(modularUI);
            return modularUI;
        } catch (RuntimeException | Error failure) {
            Data_Energistics.LOGGER.error("Failed to create the Trinity Pattern Core LDLib2 UI", failure);
            throw failure;
        }
    }
}
