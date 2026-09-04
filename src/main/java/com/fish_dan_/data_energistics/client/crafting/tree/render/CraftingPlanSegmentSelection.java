package com.fish_dan_.data_energistics.client.crafting.tree.render;

import com.fish_dan_.data_energistics.common.crafting.tree.layout.CraftingPlanRouteGeometry.SegmentRange;

import it.unimi.dsi.fastutil.ints.Int2IntAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMaps;

/** Mutable merged segment ranges for hover highlighting; the PNG exporter uses the shared immutable empty selection. */
public final class CraftingPlanSegmentSelection {

    public static final CraftingPlanSegmentSelection NONE = new CraftingPlanSegmentSelection(Int2IntSortedMaps.EMPTY_MAP);

    private final Int2IntSortedMap ranges;

    public CraftingPlanSegmentSelection() {
        this(new Int2IntAVLTreeMap());
    }

    private CraftingPlanSegmentSelection(Int2IntSortedMap ranges) {
        this.ranges = ranges;
    }

    /** Adds one route-owned continuous segment range without expanding its member ids. */
    public void add(SegmentRange range) {
        int start = range.startInclusive();
        int end = range.endExclusive();
        Int2IntSortedMap before = ranges.headMap(start + 1);
        if (!before.isEmpty()) {
            int previousStart = before.lastIntKey();
            int previousEnd = ranges.get(previousStart);
            if (previousEnd >= end) return;
            if (previousEnd >= start) {
                start = previousStart;
                end = Math.max(end, previousEnd);
                ranges.remove(previousStart);
            }
        }
        while (true) {
            Int2IntSortedMap after = ranges.tailMap(start);
            if (after.isEmpty()) break;
            int nextStart = after.firstIntKey();
            if (nextStart > end) break;
            end = Math.max(end, ranges.remove(nextStart));
        }
        ranges.put(start, end);
    }

    public boolean contains(int segmentId) {
        Int2IntSortedMap before = this.ranges.headMap(segmentId + 1);
        return !before.isEmpty() && segmentId < this.ranges.get(before.lastIntKey());
    }

    public void clear() {
        this.ranges.clear();
    }
}
