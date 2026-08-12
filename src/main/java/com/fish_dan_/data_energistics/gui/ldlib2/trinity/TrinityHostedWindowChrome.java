package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUiContext;
import com.fish_dan_.data_energistics.gui.ldlib2.host.window.HostSubUiRoot;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import org.jetbrains.annotations.NotNull;

/**
 * Shared geometry and controls for the draggable Trinity automatic-build window.
 */
final class TrinityHostedWindowChrome {

    private static final String CLOSE_TOOLTIP_KEY = "screen.data_energistics.multiblock_preview.window.close";
    private static final String WINDOW_ID = TrinityDataCoreStructureProviders.AUTO_BUILD_WINDOW_ID;
    private static final String TITLE_ID = WINDOW_ID + "_title";
    private static final String CLOSE_ID = WINDOW_ID + "_close";

    private TrinityHostedWindowChrome() {}

    /**
     * Binds the localized title and close behavior without letting the label intercept root dragging.
     */
    static void bindExisting(@NotNull HostSubUiRoot root, @NotNull HostSubUiContext context) {
        requireKnownKey(context.key());
        if (!WINDOW_ID.equals(root.getId())) {
            throw new IllegalStateException("Trinity automatic-build NBT root has unexpected id " + root.getId());
        }

        Button close = TrinityUiXmlLayouts.require(root, CLOSE_ID, Button.class);
        Label titleLabel = TrinityUiXmlLayouts.require(root, TITLE_ID, Label.class);
        titleLabel.setText(Component.translatable("screen.data_energistics.trinity_data_core.auto_build.title"));
        titleLabel.setAllowHitTest(false);
        close.setOnClick(event -> context.requestClose());
        Component closeTooltip = Component.translatable(CLOSE_TOOLTIP_KEY);
        close.text.style(style -> style.tooltips(closeTooltip));
        close.style(style -> style.tooltips(closeTooltip));
    }

    private static void requireKnownKey(HostUiKey key) {
        if (!TrinityDataCoreHostUiKeys.AUTO_BUILD.equals(key)) {
            throw new IllegalArgumentException("Unknown Trinity hosted window key " + key.id());
        }
    }
}
