package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.AeMenuBridge;
import com.fish_dan_.data_energistics.gui.ldlib2.AePlayerInventoryLayout;
import com.fish_dan_.data_energistics.gui.ldlib2.AePlayerInventoryPanel;
import com.fish_dan_.data_energistics.gui.ldlib2.HostModularUI;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiExtension;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

/**
 * Mounts the double-sided LDLib2 host tree during Trinity Data Core menu construction.
 */
public final class TrinityDataCoreHostUi {

    static final String ROOT_ID = "trinity_data_core_root";
    private static final int WIDTH = 256;
    private static final int HEIGHT = 212;
    private static final AePlayerInventoryLayout PLAYER_INVENTORY_LAYOUT = new AePlayerInventoryLayout(48, 127, 186);

    private TrinityDataCoreHostUi() {}

    /**
     * Builds and mounts the complete root after the AE2 menu has created all of its existing slots.
     *
     * @param menu menu whose server and client instances must construct an identical root tree
     * @return child-window extension owned by the mounted ModularUI lifetime
     */
    public static HostUiExtension mount(TrinityDataCoreMenu menu) {
        if (menu == null) {
            Data_Energistics.LOGGER.error("Cannot mount the Trinity Data Core LDLib2 UI without a menu");
            throw new IllegalArgumentException("Trinity Data Core menu must not be null");
        }

        AeMenuBridge bridge = AeMenuBridge.create(menu);
        UIElement root = new UIElement();
        root.setId(ROOT_ID);
        root.layout(layout -> layout.width(WIDTH).height(HEIGHT));
        HostUiExtension hostUi = HostUiExtension.create(root);
        HostModularUI modularUI = null;
        try {
            root.addChild(TrinityDataCoreStatusPanel.create(menu));
            root.addChild(AePlayerInventoryPanel.create(menu, bridge, PLAYER_INVENTORY_LAYOUT));
            modularUI = hostUi.createModularUI(UI.of(root), menu.getPlayer());
            bridge.mount(modularUI);
            return hostUi;
        } catch (RuntimeException | Error failure) {
            Data_Energistics.LOGGER.error("Failed to create the Trinity Data Core LDLib2 host UI", failure);
            try {
                if (modularUI == null) {
                    hostUi.dispose();
                } else {
                    modularUI.onRemoved();
                }
            } catch (RuntimeException | Error cleanupFailure) {
                Data_Energistics.LOGGER.error("Failed to dispose an incomplete Trinity Data Core host UI", cleanupFailure);
                if (cleanupFailure != failure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
    }
}
