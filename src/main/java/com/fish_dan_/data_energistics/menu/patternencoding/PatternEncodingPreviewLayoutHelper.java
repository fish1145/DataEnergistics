package com.fish_dan_.data_energistics.menu.patternencoding;

import com.fish_dan_.data_energistics.Data_Energistics;

public final class PatternEncodingPreviewLayoutHelper {

    public static final String ACTION_SET_PREVIEW_PANEL_OFFSET = "dataEnergistics$setPreviewPanelOffset";
    public static final String ACTION_RESET_PREVIEW_PANEL_OFFSET = "dataEnergistics$resetPreviewPanelOffset";

    private PatternEncodingPreviewLayoutHelper() {}

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
}
