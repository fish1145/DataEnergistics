package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.gui.ldlib2.HostUiExtension;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;

/**
 * Creates the always-mounted launcher for Trinity's automatic-build child UI.
 */
final class TrinityDataCoreHostLauncherPanel {

    static final String PANEL_ID = "trinity_data_core_host_launchers";
    static final String AUTO_BUILD_ID = "trinity_data_core_open_auto_build";
    private TrinityDataCoreHostLauncherPanel() {}

    /**
     * Creates the pure-client-click launcher without adding any dynamic-window RPC to the static host tree.
     *
     * @param hostUi sealed lifecycle endpoint that validates and transports toggle requests
     * @return fresh launcher rail
     */
    static UIElement create(HostUiExtension hostUi) {
        if (hostUi == null) {
            throw new IllegalArgumentException("Trinity host launcher panel requires a host UI extension");
        }
        UIElement panel = TrinityUiXmlLayouts.loadRoot("data_core_launcher");
        launcher(TrinityUiXmlLayouts.require(panel, AUTO_BUILD_ID, Button.class), hostUi);
        return panel;
    }

    private static void launcher(Button button, HostUiExtension hostUi) {
        button.noText();
        button.addPreIcon(Icons.SETTINGS);
        button.setOnClick(event -> hostUi.requestToggle(TrinityDataCoreHostUiKeys.AUTO_BUILD));
        button.style(style -> style.tooltips(
                Component.translatable("button.data_energistics.trinity_data_core.auto_build")));
    }
}
