package com.fish_dan_.data_energistics.client.screen.patternencoding;

import net.minecraft.client.gui.GuiGraphics;

/** Client-thread bridge between the native container foreground and its floating upload panels. */
interface PatternEncodingPreviewLayerScreen {

    /** Draws panels before the native carried stack; graphics still has the container-origin translation. */
    void renderPreviewForeground(GuiGraphics graphics, int mouseX, int mouseY);

    /** Uses the same GUI-space bounds for native slot hits, XEI lookups and tooltip suppression. */
    boolean isPreviewLayerAt(double mouseX, double mouseY);

    /** Returns whether a tooltip initiated by another GUI layer should be hidden at the given position. */
    boolean shouldSuppressUnderlyingTooltip(int mouseX, int mouseY);
}
