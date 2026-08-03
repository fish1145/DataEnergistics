package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carries a globally optimized firing vector together with its proven conservation-seed lower bound.
 *
 * @param firings               exact non-negative firing vector
 * @param minimumExternalInput  global minimum external reserve at the preceding objective level
 * @param minimumSeedLowerBound global lower bound for the total internal seed at the preceding objective levels
 */
public record TrinityFiringOptimization(
                                        Map<TrinityPatternVariant, BigInteger> firings,
                                        BigInteger minimumExternalInput,
                                        BigInteger minimumSeedLowerBound) {

    public TrinityFiringOptimization {
        if (firings == null || firings.isEmpty() || minimumExternalInput == null ||
                minimumSeedLowerBound == null || minimumExternalInput.signum() < 0 ||
                minimumSeedLowerBound.signum() < 0) {
            throw new IllegalArgumentException("A Trinity firing optimization requires an exact non-empty proof");
        }
        LinkedHashMap<TrinityPatternVariant, BigInteger> copied = new LinkedHashMap<>();
        firings.forEach((variant, count) -> {
            if (variant == null || count == null || count.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity optimized firing must be positive");
            }
            copied.put(variant, count);
        });
        firings = Collections.unmodifiableMap(copied);
    }
}
