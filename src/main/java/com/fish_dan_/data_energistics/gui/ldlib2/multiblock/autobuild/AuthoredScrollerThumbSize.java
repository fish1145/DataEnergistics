package com.fish_dan_.data_energistics.gui.ldlib2.multiblock.autobuild;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Scroller;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

/** Keeps LDLib2's logical travel calculation aligned with the fixed thumb length authored in NBT. */
final class AuthoredScrollerThumbSize {

    private static final float SIZE_EPSILON = 0.0001F;

    private AuthoredScrollerThumbSize() {}

    static void bind(Scroller.Vertical scroller) {
        collapseStepButtons(scroller);
        var authoredTrack = scroller.scrollBar.getParent();
        if (authoredTrack == null || authoredTrack.getParent() != scroller.scrollContainer) {
            throw new IllegalStateException("Vertical authored scroller thumb is detached from its track");
        }
        authoredTrack.addEventListener(
                UIEvents.LAYOUT_CHANGED,
                ignored -> synchronize(
                        scroller,
                        scroller.scrollBar.getSizeHeight(),
                        authoredTrack.getContentHeight()));
    }

    static void bind(Scroller.Horizontal scroller, AutoBuildComposition.HorizontalSpan track) {
        collapseStepButtons(scroller);
        var authoredTrack = scroller.scrollBar.getParent();
        if (authoredTrack == null || authoredTrack.getParent() != scroller.scrollContainer) {
            throw new IllegalStateException("Horizontal authored scroller thumb is detached from its track");
        }
        scroller.scrollContainer.layout(layout -> layout
                .left(track.left())
                .width(track.width())
                .minWidth(track.width())
                .maxWidth(track.width())
                .setFlexGrow(0)
                .setFlexShrink(0));
        authoredTrack.addEventListener(
                UIEvents.LAYOUT_CHANGED,
                ignored -> synchronize(
                        scroller,
                        scroller.scrollBar.getSizeWidth(),
                        authoredTrack.getContentWidth()));
    }

    /** Removes LDLib2's default five-pixel step-button reservations from arrowless authored rails. */
    private static void collapseStepButtons(Scroller scroller) {
        scroller.headButton.setDisplay(false);
        scroller.tailButton.setDisplay(false);
        scroller.layout(layout -> layout
                .gapRow(0)
                .gapColumn(0));
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
