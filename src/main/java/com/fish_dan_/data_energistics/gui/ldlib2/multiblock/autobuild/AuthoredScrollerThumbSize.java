package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.autobuild;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

/** Keeps LDLib2's logical travel calculation aligned with the fixed thumb length authored in NBT. */
final class AuthoredScrollerThumbSize {

    private static final float SIZE_EPSILON = 0.0001F;

    private AuthoredScrollerThumbSize() {}

    static void bind(Scroller.Vertical scroller) {
        scroller.scrollContainer.addEventListener(
                UIEvents.LAYOUT_CHANGED,
                ignored -> synchronize(
                        scroller,
                        scroller.scrollBar.getSizeHeight(),
                        scroller.scrollContainer.getContentHeight()));
    }

    static void bind(Scroller.Horizontal scroller) {
        scroller.scrollContainer.addEventListener(
                UIEvents.LAYOUT_CHANGED,
                ignored -> synchronize(
                        scroller,
                        scroller.scrollBar.getSizeWidth(),
                        scroller.scrollContainer.getContentWidth()));
    }

    private static void synchronize(Scroller scroller, float thumbLength, float trackLength) {
        if (thumbLength <= 0.0F || trackLength <= 0.0F) {
            return;
        }
        float percentage = Math.min(100.0F, thumbLength * 100.0F / trackLength);
        if (Math.abs(scroller.getScrollerStyle().scrollBarSize() - percentage) > SIZE_EPSILON) {
            scroller.setScrollBarSize(percentage);
        }
    }
}
