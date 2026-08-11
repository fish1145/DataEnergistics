package com.fish_dan_.data_energistics.client.screen.patternencoding;

/** Controls whether tooltips from lower GUI layers are visible below a floating preview. */
interface PreviewLayerTooltipScreen {

    /** Returns whether a tooltip initiated by another GUI layer should be hidden at the given position. */
    boolean shouldSuppressUnderlyingTooltip(int mouseX, int mouseY);
}
