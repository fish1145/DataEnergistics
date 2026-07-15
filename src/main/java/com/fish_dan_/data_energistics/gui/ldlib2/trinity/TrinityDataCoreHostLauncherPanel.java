package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.gui.ldlib2.HostUiExtension;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiKey;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.TaffyPosition;

/** Creates the always-mounted launcher rail for Trinity's four independently hosted child UIs. */
final class TrinityDataCoreHostLauncherPanel {

    static final String PANEL_ID = "trinity_data_core_host_launchers";
    static final String MAIN_ID = "trinity_data_core_open_main";
    static final String CPU_ID = "trinity_data_core_open_cpu";
    static final String CRAFTING_ID = "trinity_data_core_open_crafting";
    static final String AUTO_BUILD_ID = "trinity_data_core_open_auto_build";
    static final int PANEL_Z_INDEX = 400;

    private static final int BUTTON_SIZE = 14;
    private static final int BUTTON_GAP = 2;

    private TrinityDataCoreHostLauncherPanel() {}

    /**
     * Creates four pure-client-click launchers without adding any dynamic-window RPC to the static host tree.
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
                .left(241)
                .top(19)
                .width(BUTTON_SIZE)
                .height(BUTTON_SIZE * 4 + BUTTON_GAP * 3));
        Style.importantPipeline(panel.getStyle(), style -> style.zIndex(PANEL_Z_INDEX));
        panel.addChildren(
                launcher(
                        MAIN_ID,
                        TrinityDataCoreHostUiKeys.MAIN,
                        Icons.MODEL,
                        "screen.data_energistics.trinity_data_core.auto_build.structure.main",
                        hostUi,
                        0),
                launcher(
                        CPU_ID,
                        TrinityDataCoreHostUiKeys.CPU,
                        Icons.MESH,
                        "screen.data_energistics.trinity_data_core.auto_build.structure.cpu",
                        hostUi,
                        1),
                launcher(
                        CRAFTING_ID,
                        TrinityDataCoreHostUiKeys.CRAFTING,
                        Icons.GRID,
                        "screen.data_energistics.trinity_data_core.auto_build.structure.crafting",
                        hostUi,
                        2),
                launcher(
                        AUTO_BUILD_ID,
                        TrinityDataCoreHostUiKeys.AUTO_BUILD,
                        Icons.SETTINGS,
                        "button.data_energistics.trinity_data_core.auto_build",
                        hostUi,
                        3));
        return panel;
    }

    private static Button launcher(String id,
                                   HostUiKey key,
                                   IGuiTexture icon,
                                   String tooltipKey,
                                   HostUiExtension hostUi,
                                   int row) {
        Button button = new Button();
        button.setId(id);
        button.noText();
        button.addPreIcon(icon);
        button.setOnClick(event -> hostUi.requestToggle(key));
        button.style(style -> style.tooltips(Component.translatable(tooltipKey)));
        button.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0)
                .top(row * (BUTTON_SIZE + BUTTON_GAP))
                .width(BUTTON_SIZE)
                .height(BUTTON_SIZE));
        return button;
    }
}
