package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.gui.ldlib2.HostUiExtension;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.TaffyPosition;

/**
 * Creates the always-mounted launcher for Trinity's automatic-build child UI.
 */
final class TrinityDataCoreHostLauncherPanel {

    static final String PANEL_ID = "trinity_data_core_host_launchers";
    static final String AUTO_BUILD_ID = "trinity_data_core_open_auto_build";
    static final int PANEL_Z_INDEX = 400;

    private static final int BUTTON_SIZE = 14;

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
        UIElement panel = new UIElement();
        panel.setId(PANEL_ID);
        panel.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(238)
                .top(1)
                .width(BUTTON_SIZE)
                .height(BUTTON_SIZE));
        Style.importantPipeline(panel.getStyle(), style -> style.zIndex(PANEL_Z_INDEX));
        panel.addChild(launcher(hostUi));
        return panel;
    }

    private static Button launcher(HostUiExtension hostUi) {
        Button button = new Button();
        button.setId(AUTO_BUILD_ID);
        button.noText();
        button.addPreIcon(Icons.SETTINGS);
        button.setOnClick(event -> hostUi.requestToggle(TrinityDataCoreHostUiKeys.AUTO_BUILD));
        button.style(style -> style.tooltips(
                Component.translatable("button.data_energistics.trinity_data_core.auto_build")));
        button.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(0)
                .width(BUTTON_SIZE)
                .height(BUTTON_SIZE));
        return button;
    }
}
