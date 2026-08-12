package com.fish_dan_.data_energistics.gui.ldlib2.trinity.core;

import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostUiExtension;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.layout.TrinityUiXmlLayouts;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;

/**
 * Binds the actions supported by the Trinity Data Core's editor-authored launcher rail.
 */
final class TrinityDataCoreHostLauncherPanel {

    static final String PANEL_ID = "trinity_data_core_host_launchers";
    static final String AUTO_BUILD_ID = "trinity_data_core_open_auto_build";
    static final String STORAGE_ID = "trinity_data_core_open_storage";
    static final String PATTERN_CORE_ID = "trinity_data_core_open_pattern_core";

    private TrinityDataCoreHostLauncherPanel() {}

    /**
     * Binds the existing pure-client-click launcher without duplicating editor-authored icons or hit areas.
     *
     * @param root   complete NBT layout root
     * @param hostUi sealed lifecycle endpoint that validates and transports toggle requests
     */
    static void bindExisting(UIElement root, HostUiExtension hostUi) {
        if (hostUi == null) {
            throw new IllegalArgumentException("Trinity host launcher panel requires a host UI extension");
        }
        TrinityUiXmlLayouts.require(root, PANEL_ID, UIElement.class);
        launcher(
                TrinityUiXmlLayouts.require(root, AUTO_BUILD_ID, Button.class),
                hostUi,
                TrinityDataCoreHostUiKeys.AUTO_BUILD,
                "button.data_energistics.trinity_data_core.auto_build");
        launcher(
                TrinityUiXmlLayouts.require(root, STORAGE_ID, Button.class),
                hostUi,
                TrinityDataCoreHostUiKeys.STORAGE,
                "button.data_energistics.trinity_data_core.storage");
        Button patternCore = TrinityUiXmlLayouts.require(root, PATTERN_CORE_ID, Button.class);
        patternCore.setActive(false);
        patternCore.setAllowHitTest(false);
    }

    private static void launcher(Button button,
                                 HostUiExtension hostUi,
                                 HostUiKey key,
                                 String tooltipKey) {
        Component tooltip = Component.translatable(tooltipKey);
        button.setOnClick(event -> hostUi.requestToggle(key));
        button.text.style(style -> style.tooltips(tooltip));
        button.style(style -> style.tooltips(tooltip));
    }
}
