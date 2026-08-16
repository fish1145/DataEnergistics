package com.fish_dan_.data_energistics.client.crafting.confirm.table;

import com.fish_dan_.data_energistics.client.crafting.confirm.presentation.TrinityCraftConfirmCyclePalette;
import com.fish_dan_.data_energistics.menu.crafting.projection.cycle.model.TrinityCraftingCycleMaterialContribution;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes equal-width cycle-color segments without encoding contribution amounts in their widths.
 */
final class TrinityCraftConfirmCycleBarLayout {

    private static final int OVERFLOW_COLOR = 0xFFF2F2F2;

    private TrinityCraftConfirmCycleBarLayout() {}

    /**
     * Lays out all visible memberships and reserves the final pixel for overflow when required.
     */
    static List<Segment> segments(List<TrinityCraftingCycleMaterialContribution> contributions,
                                  int pixelWidth) {
        if (contributions.isEmpty()) {
            return List.of();
        }

        if (contributions.size() > pixelWidth) {
            ArrayList<Segment> segments = new ArrayList<>(pixelWidth);
            int visibleMemberships = pixelWidth - 1;
            for (int index = 0; index < visibleMemberships; index++) {
                segments.add(new Segment(
                        index,
                        index + 1,
                        TrinityCraftConfirmCyclePalette.argb(contributions.get(index).displayOrdinal())));
            }
            segments.add(new Segment(visibleMemberships, pixelWidth, OVERFLOW_COLOR));
            return List.copyOf(segments);
        }

        int segmentCount = contributions.size();
        ArrayList<Segment> segments = new ArrayList<>(segmentCount);
        for (int index = 0; index < segmentCount; index++) {
            int start = index * pixelWidth / segmentCount;
            int end = (index + 1) * pixelWidth / segmentCount;
            segments.add(new Segment(
                    start,
                    end,
                    TrinityCraftConfirmCyclePalette.argb(contributions.get(index).displayOrdinal())));
        }
        return List.copyOf(segments);
    }

    /** One half-open horizontal color interval within a cell-width bar. */
    record Segment(int start, int end, int color) {}
}
