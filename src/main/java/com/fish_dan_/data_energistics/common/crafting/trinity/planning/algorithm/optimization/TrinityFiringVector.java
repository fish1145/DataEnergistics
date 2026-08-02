package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Complete exact firing identity over one stable, sorted variant domain.
 */
public final class TrinityFiringVector implements Comparable<TrinityFiringVector> {

    private final List<TrinityPatternVariant> variants;
    private final List<BigInteger> counts;

    private TrinityFiringVector(List<TrinityPatternVariant> variants, List<BigInteger> counts) {
        this.variants = variants;
        this.counts = counts;
    }

    /**
     * Builds a complete vector, inserting exact zeroes for omitted variants.
     *
     * @param domain  complete variant domain
     * @param firings sparse or complete non-negative firing map
     * @return immutable complete numeric vector
     */
    public static TrinityFiringVector from(
                                           List<TrinityPatternVariant> domain,
                                           Map<TrinityPatternVariant, BigInteger> firings) {
        if (domain == null || domain.isEmpty() || firings == null) {
            throw new IllegalArgumentException("A Trinity firing vector requires a non-empty domain and firing map");
        }
        List<TrinityPatternVariant> orderedVariants = new ArrayList<>(domain);
        if (orderedVariants.stream().anyMatch(variant -> variant == null)) {
            throw new IllegalArgumentException("A Trinity firing vector domain cannot contain null variants");
        }
        Collections.sort(orderedVariants);
        Set<TrinityPatternVariant> uniqueVariants = new HashSet<>(orderedVariants);
        if (uniqueVariants.size() != orderedVariants.size() || !uniqueVariants.containsAll(firings.keySet())) {
            throw new IllegalArgumentException("A Trinity firing vector requires a unique complete variant domain");
        }
        List<BigInteger> orderedCounts = new ArrayList<>(orderedVariants.size());
        for (TrinityPatternVariant variant : orderedVariants) {
            BigInteger count = firings.getOrDefault(variant, BigInteger.ZERO);
            if (count == null || count.signum() < 0) {
                throw new IllegalArgumentException("Trinity firing counts must be non-negative exact integers");
            }
            orderedCounts.add(count);
        }
        return new TrinityFiringVector(List.copyOf(orderedVariants), List.copyOf(orderedCounts));
    }

    /**
     * @return complete stable variant order
     */
    public List<TrinityPatternVariant> variants() {
        return this.variants;
    }

    /**
     * @return exact counts aligned with {@link #variants()}
     */
    public List<BigInteger> counts() {
        return this.counts;
    }

    @Override
    public int compareTo(@NotNull TrinityFiringVector other) {
        if (!this.variants.equals(other.variants)) {
            throw new IllegalArgumentException("Trinity firing vectors must share the same complete variant domain");
        }
        for (int index = 0; index < this.counts.size(); index++) {
            int compared = this.counts.get(index).compareTo(other.counts.get(index));
            if (compared != 0) {
                return compared;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TrinityFiringVector vector)) {
            return false;
        }
        return this.variants.equals(vector.variants) && this.counts.equals(vector.counts);
    }

    @Override
    public int hashCode() {
        return 31 * this.variants.hashCode() + this.counts.hashCode();
    }
}
