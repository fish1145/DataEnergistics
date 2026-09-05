package com.fish_dan_.data_energistics.client.screen.patternencoding;

import appeng.client.gui.Icon;
import appeng.client.gui.widgets.IconButton;

import net.minecraft.network.chat.Component;

final class PatternEncodingPreviewDragButton extends IconButton {

    PatternEncodingPreviewDragButton() {
        this(Component.translatable("screen.data_energistics.pattern_writer_preview.drag_handle"));
    }

    PatternEncodingPreviewDragButton(Component message) {
        super(button -> {});
        setMessage(message);
    }

    @Override
    protected Icon getIcon() {
        return Icon.COG;
    }
}
