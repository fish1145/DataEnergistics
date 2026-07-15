package com.fish_dan_.data_energistics.gui.ldlib2.trinity;

import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiContext;
import com.fish_dan_.data_energistics.gui.ldlib2.HostSubUiRoot;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiKey;

import net.minecraft.network.chat.Component;

import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import dev.vfyjxf.taffy.style.TaffyPosition;

/** Shared geometry and controls for the four draggable Trinity hosted windows. */
final class TrinityHostedWindowChrome {

    static final int WINDOW_WIDTH = 292;
    static final int WINDOW_HEIGHT = 210;
    static final int PREVIEW_WIDTH = 196;
    static final int CONTENT_HEIGHT = 184;
    static final int SIDE_WIDTH = 84;

    private static final int TITLE_LEFT = 2;
    private static final int TITLE_TOP = 2;
    private static final int TITLE_WIDTH = 272;
    private static final int TITLE_HEIGHT = 16;
    private static final int CLOSE_BUTTON_SIZE = 16;
    private static final int CLOSE_LEFT = 274;
    private static final int PREVIEW_LEFT = 4;
    private static final int CONTENT_TOP = 22;
    private static final int SIDE_LEFT = 204;
    private static final String CLOSE_TOOLTIP_KEY = "screen.data_energistics.multiblock_preview.window.close";

    private TrinityHostedWindowChrome() {}

    /** Applies the fresh window's default placement; the host later overrides it with any saved position. */
    static void configureRoot(HostSubUiRoot root, HostUiKey key) {
        WindowOffset offset = defaultOffset(key);
        root.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(offset.left())
                .top(offset.top())
                .width(WINDOW_WIDTH)
                .height(WINDOW_HEIGHT));
        root.style(style -> style.backgroundTexture(Sprites.BORDER));
    }

    /** Creates the common draggable title surface and close command without duplicating chrome coordinates. */
    static Chrome create(String windowId, Component title, HostSubUiContext context) {
        if (windowId == null || title == null || context == null) {
            throw new IllegalArgumentException("Trinity hosted window chrome arguments cannot be null");
        }

        UIElement dragHandle = new UIElement();
        dragHandle.setId(windowId + "_drag_handle");
        dragHandle.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(TITLE_LEFT)
                .top(TITLE_TOP)
                .width(TITLE_WIDTH)
                .height(TITLE_HEIGHT));

        Label titleLabel = new Label();
        titleLabel.setText(title);
        titleLabel.textStyle(style -> style
                .adaptiveWidth(false)
                .adaptiveHeight(false)
                .fontSize(8.5f)
                .textColor(0xFFFFFFFF)
                .textAlignVertical(Vertical.CENTER)
                .textWrap(TextWrap.HOVER_ROLL)
                .textShadow(false));
        titleLabel.setOverflowVisible(false);
        titleLabel.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(5)
                .top(0)
                .width(TITLE_WIDTH - 10)
                .height(TITLE_HEIGHT));
        dragHandle.addChild(titleLabel);

        Button close = new Button();
        close.setId(windowId + "_close");
        close.noText();
        close.addPreIcon(Icons.CLOSE);
        close.setOnClick(event -> context.requestClose());
        close.style(style -> style.tooltips(Component.translatable(CLOSE_TOOLTIP_KEY)));
        close.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(CLOSE_LEFT)
                .top(TITLE_TOP)
                .width(CLOSE_BUTTON_SIZE)
                .height(CLOSE_BUTTON_SIZE));
        return new Chrome(dragHandle, close);
    }

    /** Pins the large scene-first preview to the left content region. */
    static void layoutPreview(UIElement preview) {
        preview.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(PREVIEW_LEFT)
                .top(CONTENT_TOP)
                .width(PREVIEW_WIDTH)
                .height(CONTENT_HEIGHT));
    }

    /** Pins status or automatic-build actions to the compact right content region. */
    static void layoutSidePanel(UIElement sidePanel) {
        sidePanel.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(SIDE_LEFT)
                .top(CONTENT_TOP)
                .width(SIDE_WIDTH)
                .height(CONTENT_HEIGHT));
    }

    static WindowOffset defaultOffset(HostUiKey key) {
        if (TrinityDataCoreHostUiKeys.MAIN.equals(key)) {
            return new WindowOffset(-104, -24);
        }
        if (TrinityDataCoreHostUiKeys.CPU.equals(key)) {
            return new WindowOffset(-72, -16);
        }
        if (TrinityDataCoreHostUiKeys.CRAFTING.equals(key)) {
            return new WindowOffset(-40, -8);
        }
        if (TrinityDataCoreHostUiKeys.AUTO_BUILD.equals(key)) {
            return new WindowOffset(-8, 0);
        }
        throw new IllegalArgumentException("Unknown Trinity hosted window key " + key.id());
    }

    record Chrome(UIElement dragHandle, Button closeButton) {}

    record WindowOffset(int left, int top) {}
}
