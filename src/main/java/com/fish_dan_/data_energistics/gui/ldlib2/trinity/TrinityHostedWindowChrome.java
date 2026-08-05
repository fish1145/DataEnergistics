package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiContext;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiRoot;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiKey;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;

/**
 * Shared geometry and controls for the draggable Trinity automatic-build window.
 */
final class TrinityHostedWindowChrome {

    private static final String CLOSE_TOOLTIP_KEY = "screen.data_energistics.multiblock_preview.window.close";

    private TrinityHostedWindowChrome() {}

    /**
     * Validates the hosted-window key before the XML and LSS tree is mounted.
     */
    static void configureRoot(HostSubUiRoot root, HostUiKey key) {
        requireKnownKey(key);
        root.addClass("trinity-hosted-window");
        root.style(style -> style.backgroundTexture(Sprites.BORDER));
    }

    /**
     * Creates the common draggable title surface and close command without duplicating chrome coordinates.
     */
    static Chrome create(String windowId, Component title, HostSubUiContext context) {
        if (windowId == null || title == null || context == null) {
            throw new IllegalArgumentException("Trinity hosted window chrome arguments cannot be null");
        }

        UIElement template = TrinityUiXmlLayouts.loadRoot("auto_build_window");
        UIElement dragHandle = TrinityUiXmlLayouts.require(template, windowId + "_drag_handle", UIElement.class);
        Button close = TrinityUiXmlLayouts.require(template, windowId + "_close", Button.class);
        Label titleLabel = TrinityUiXmlLayouts.require(dragHandle, windowId + "_title", Label.class);
        titleLabel.setText(title);
        close.noText();
        close.addPreIcon(Icons.CLOSE);
        close.setOnClick(event -> context.requestClose());
        close.style(style -> style.tooltips(Component.translatable(CLOSE_TOOLTIP_KEY)));
        if (!template.removeChild(dragHandle) || !template.removeChild(close)) {
            throw new IllegalStateException("Trinity automatic-build window XML failed to detach chrome elements");
        }
        return new Chrome(dragHandle, close);
    }

    /**
     * Pins the large scene-first preview to the left content region.
     */
    static void layoutPreview(UIElement preview) {
        preview.addClass("trinity-hosted-preview");
    }

    /**
     * Pins status or automatic-build actions to the compact right content region.
     */
    static void layoutSidePanel(UIElement sidePanel) {
        sidePanel.addClass("trinity-hosted-side-panel");
    }

    private static void requireKnownKey(HostUiKey key) {
        if (key == null) {
            throw new IllegalArgumentException("Trinity hosted window key cannot be null");
        }
        if (!TrinityDataCoreHostUiKeys.AUTO_BUILD.equals(key)) {
            throw new IllegalArgumentException("Unknown Trinity hosted window key " + key.id());
        }
    }

    record Chrome(UIElement dragHandle, Button closeButton) {}
}
