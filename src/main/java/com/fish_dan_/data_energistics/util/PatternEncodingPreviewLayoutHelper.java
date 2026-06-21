package com.fish_dan_.data_energistics.util;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingPreviewLayoutAware;

import net.minecraft.nbt.CompoundTag;

import appeng.helpers.IPatternTerminalMenuHost;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.PatternEncodingLogic;

public final class PatternEncodingPreviewLayoutHelper {

    public static final String ACTION_SET_PREVIEW_PANEL_OFFSET = "dataEnergistics$setPreviewPanelOffset";
    public static final String ACTION_RESET_PREVIEW_PANEL_OFFSET = "dataEnergistics$resetPreviewPanelOffset";
    private static final String TAG_PREVIEW_LAYOUT = "data_energistics_preview_layout";
    private static final String TAG_OFFSET_X = "offset_x";
    private static final String TAG_OFFSET_Y = "offset_y";

    private PatternEncodingPreviewLayoutHelper() {}

    public static int readOffsetX(CompoundTag data) {
        return readOffset(data, TAG_OFFSET_X);
    }

    public static int readOffsetY(CompoundTag data) {
        return readOffset(data, TAG_OFFSET_Y);
    }

    public static void writeOffset(CompoundTag data, int offsetX, int offsetY) {
        if (offsetX == 0 && offsetY == 0) {
            data.remove(TAG_PREVIEW_LAYOUT);
            return;
        }

        CompoundTag layout = data.getCompound(TAG_PREVIEW_LAYOUT);
        layout.putInt(TAG_OFFSET_X, offsetX);
        layout.putInt(TAG_OFFSET_Y, offsetY);
        data.put(TAG_PREVIEW_LAYOUT, layout);
    }

    public static void applySetOffsetAction(PatternEncodingPreviewLayoutAware layoutAware, String payload) {
        if (payload == null) {
            return;
        }

        int separator = payload.indexOf(',');
        if (separator < 0) {
            return;
        }

        try {
            int offsetX = Integer.parseInt(payload.substring(0, separator));
            int offsetY = Integer.parseInt(payload.substring(separator + 1));
            layoutAware.data_energistics$setPreviewPanelOffset(offsetX, offsetY);
        } catch (NumberFormatException e) {
            Data_Energistics.LOGGER
                    .error("Invalid Data Energistics preview panel offset payload: {}", payload, e);
        }
    }

    public static PatternEncodingLogic getLogic(PatternEncodingTermMenu menu) {
        if (!(menu.getHost() instanceof IPatternTerminalMenuHost host)) {
            throw new IllegalStateException("Pattern terminal host does not expose encoding logic: " + menu.getHost().getClass().getName());
        }
        return host.getLogic();
    }

    private static int readOffset(CompoundTag data, String key) {
        CompoundTag layout = data.getCompound(TAG_PREVIEW_LAYOUT);
        return layout.getInt(key);
    }
}
