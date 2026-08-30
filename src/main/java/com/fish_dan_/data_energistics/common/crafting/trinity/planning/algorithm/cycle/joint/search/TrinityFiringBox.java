package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityFiringBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Stable Cartesian firing domain used by exact first-difference branch-and-bound.
 *
 * @param variants stable sorted firing axes
 * @param bounds   exact inclusive bounds aligned with {@code variants}
 */
public record TrinityFiringBox(
                               List<TrinityPatternVariant> variants,
                               List<TrinityFiringBounds> bounds) {

    /**
     * Validates and freezes one complete non-empty box.
     */
    public TrinityFiringBox {
        if (variants == null || variants.isEmpty() || bounds == null || variants.size() != bounds.size() ||
                variants.stream().anyMatch(variant -> variant == null) ||
                bounds.stream().anyMatch(bound -> bound == null)) {
            throw new IllegalArgumentException("A Trinity firing box requires aligned variants and bounds");
        }
        variants = List.copyOf(variants);
        if (!variants.equals(variants.stream().sorted().toList())) {
            throw new IllegalArgumentException("A Trinity firing box requires stable sorted variants");
        }
        if (new LinkedHashSet<>(variants).size() != variants.size()) {
            throw new IllegalArgumentException("A Trinity firing box cannot repeat a variant");
        }
        bounds = List.copyOf(bounds);
    }

    /**
     * Creates the complete downstream-representable search domain.
     */
    public static TrinityFiringBox full(List<TrinityPatternVariant> variants) {
        List<TrinityPatternVariant> ordered = variants.stream().sorted().toList();
        return new TrinityFiringBox(
                ordered,
                ordered.stream().map(variant -> TrinityFiringBounds.full()).toList());
    }

    /**
     * @return stable map accepted by the exact feasibility backend
     */
    public Map<TrinityPatternVariant, TrinityFiringBounds> asMap() {
        LinkedHashMap<TrinityPatternVariant, TrinityFiringBounds> mapped = new LinkedHashMap<>();
        for (int index = 0; index < variants.size(); index++) {
            mapped.put(variants.get(index), bounds.get(index));
        }
        return Collections.unmodifiableMap(mapped);
    }

    /**
     * @return exact lower bound for the total firing count in this box
     */
    public BigInteger totalLowerBound() {
        return bounds.stream()
                .map(TrinityFiringBounds::lowerInclusive)
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    /**
     * Partitions one stable, most-constrained axis into lower, equal and upper slices. The equal slice is retained only
     * while another axis can still differ, so the disjoint children cover this box minus {@code candidate} with at most
     * three boxes instead of eagerly creating two boxes per axis.
     */
    public List<TrinityFiringBox> excluding(Map<TrinityPatternVariant, BigInteger> candidate) {
        if (candidate == null) {
            throw new IllegalArgumentException("A Trinity firing-box split requires a candidate");
        }
        ArrayList<BigInteger> vector = new ArrayList<>(variants.size());
        for (int index = 0; index < variants.size(); index++) {
            BigInteger value = candidate.getOrDefault(variants.get(index), BigInteger.ZERO);
            if (!bounds.get(index).contains(value)) {
                throw new IllegalArgumentException("A Trinity firing-box candidate must lie inside its parent box");
            }
            vector.add(value);
        }

        int splitAxis = mostConstrainedAxis();
        if (splitAxis < 0) {
            return List.of();
        }
        TrinityFiringBounds parent = bounds.get(splitAxis);
        BigInteger value = vector.get(splitAxis);
        ArrayList<TrinityFiringBox> children = new ArrayList<>(3);
        if (value.compareTo(parent.lowerInclusive()) > 0) {
            children.add(withBounds(
                    splitAxis,
                    new TrinityFiringBounds(parent.lowerInclusive(), value.subtract(BigInteger.ONE))));
        }
        if (hasAnotherOpenAxis(splitAxis)) {
            children.add(withBounds(splitAxis, TrinityFiringBounds.fixed(value)));
        }
        if (value.compareTo(parent.upperInclusive()) < 0) {
            children.add(withBounds(
                    splitAxis,
                    new TrinityFiringBounds(value.add(BigInteger.ONE), parent.upperInclusive())));
        }
        return List.copyOf(children);
    }

    private int mostConstrainedAxis() {
        int selected = -1;
        BigInteger selectedWidth = null;
        for (int index = 0; index < bounds.size(); index++) {
            TrinityFiringBounds candidate = bounds.get(index);
            BigInteger width = candidate.upperInclusive().subtract(candidate.lowerInclusive());
            if (width.signum() > 0 && (selectedWidth == null || width.compareTo(selectedWidth) < 0)) {
                selected = index;
                selectedWidth = width;
            }
        }
        return selected;
    }

    private boolean hasAnotherOpenAxis(int excluded) {
        for (int index = 0; index < bounds.size(); index++) {
            if (index != excluded &&
                    bounds.get(index).lowerInclusive().compareTo(bounds.get(index).upperInclusive()) < 0) {
                return true;
            }
        }
        return false;
    }

    private TrinityFiringBox withBounds(int axis, TrinityFiringBounds value) {
        ArrayList<TrinityFiringBounds> child = new ArrayList<>(bounds);
        child.set(axis, value);
        return new TrinityFiringBox(variants, child);
    }
}
