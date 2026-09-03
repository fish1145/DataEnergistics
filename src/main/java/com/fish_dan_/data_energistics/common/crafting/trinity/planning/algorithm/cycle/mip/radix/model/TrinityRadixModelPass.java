package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Map;

/**
 * Describes one stage of the sequential lexicographic solve without encoding priorities as a weighted scalar.
 */
public sealed interface TrinityRadixModelPass {

    /**
     * Selects the first objective: minimum total external input.
     */
    enum External implements TrinityRadixModelPass {
        INSTANCE
    }

    /**
     * Fixes the external optimum and selects the minimum initial SCC seed.
     *
     * @param fixedExternal  exact first-objective value
     * @param seedLowerBound lower level requested after an unschedulable candidate
     */
    record Seed(BigInteger fixedExternal, BigInteger seedLowerBound) implements TrinityRadixModelPass {}

    /**
     * Fixes input objectives and selects the minimum total logical pattern firings.
     *
     * @param fixedExternal    exact external-input optimum
     * @param fixedSeed        exact seed optimum
     * @param firingLowerBound lower level requested after an unschedulable firing vector
     */
    record Firing(
                  BigInteger fixedExternal,
                  BigInteger fixedSeed,
                  BigInteger firingLowerBound)
            implements TrinityRadixModelPass {}

    /**
     * Fixes all preceding objectives and maximises one stable firing axis to produce a deterministic vector.
     *
     * @param fixedExternal exact external-input optimum
     * @param fixedSeed     exact seed optimum
     * @param fixedFirings  exact total firing optimum
     * @param fixedCounts   already selected stable axes
     * @param variant       current stable identity axis
     */
    record Identity(
                    BigInteger fixedExternal,
                    BigInteger fixedSeed,
                    BigInteger fixedFirings,
                    Map<TrinityPatternVariant, BigInteger> fixedCounts,
                    TrinityPatternVariant variant)
            implements TrinityRadixModelPass {

        public Identity {
            fixedCounts = Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(fixedCounts));
        }
    }

    /**
     * Builds a finite feasibility domain without running any sequential objective search.
     */
    enum Feasibility implements TrinityRadixModelPass {
        INSTANCE
    }
}
