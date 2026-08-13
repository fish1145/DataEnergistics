package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.autobuild;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import org.jetbrains.annotations.NotNull;

/**
 * Maps a scroller value onto the resolved travel of an editor-authored, pixel-constrained thumb without changing
 * layout.
 */
final class AuthoredScrollerThumbTravel {

    private AuthoredScrollerThumbTravel() {}

    static void bind(@NotNull Scroller.Vertical scroller) {
        scroller.setOnValueChanged(ignored -> align(scroller));
        scroller.scrollBar.addEventListener(UIEvents.LAYOUT_CHANGED, ignored -> align(scroller));
    }

    static void bind(@NotNull Scroller.Horizontal scroller) {
        scroller.setOnValueChanged(ignored -> align(scroller));
        scroller.scrollBar.addEventListener(UIEvents.LAYOUT_CHANGED, ignored -> align(scroller));
    }

    static void align(@NotNull Scroller.Vertical scroller) {
        float trackLength = scroller.scrollContainer.getContentHeight();
        float thumbLength = scroller.scrollBar.getSizeHeight();
        if (trackLength <= 0.0F || thumbLength <= 0.0F || thumbLength > trackLength) {
            return;
        }
        float styledThumbLength = trackLength * scroller.getScrollerStyle().scrollBarSize() / 100.0F;
        float translation = scroller.getNormalizedValue() * (styledThumbLength - thumbLength);
        scroller.scrollBar.transform(transform -> transform.translate(0.0F, translation));
    }

    static void align(@NotNull Scroller.Horizontal scroller) {
        float trackLength = scroller.scrollContainer.getContentWidth();
        float thumbLength = scroller.scrollBar.getSizeWidth();
        if (trackLength <= 0.0F || thumbLength <= 0.0F || thumbLength > trackLength) {
            return;
        }
        float styledThumbLength = trackLength * scroller.getScrollerStyle().scrollBarSize() / 100.0F;
        float translation = scroller.getNormalizedValue() * (styledThumbLength - thumbLength);
        scroller.scrollBar.transform(transform -> transform.translate(translation, 0.0F));
    }
}
