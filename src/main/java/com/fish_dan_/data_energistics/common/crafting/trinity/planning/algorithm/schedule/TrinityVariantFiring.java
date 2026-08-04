package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import java.math.BigInteger;

/**
 * Compact exact firing count for one fully bound transition.
 *
 * @param variant immutable bound transition
 * @param count   positive logical firing count
 */
public record TrinityVariantFiring(TrinityPatternVariant variant, BigInteger count) {

    /**
     * Rejects empty schedule entries.
     */
    public TrinityVariantFiring {
        if (variant == null || count == null || count.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity variant firing requires a variant and positive count");
        }
    }
}
