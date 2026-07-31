package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;

import java.math.BigInteger;

/**
 * One compact pattern firing vector entry inside a stage.
 *
 * @param patternIdentity stable published pattern semantics
 * @param variantOrdinal  deterministic legal input-binding ordinal
 * @param count           logical firing count without per-request expansion
 */
public record TrinityPlanPatternFiring(
                                       TrinityPatternIdentity patternIdentity,
                                       int variantOrdinal,
                                       BigInteger count) {

    /** Rejects unbound or non-productive scheduling entries. */
    public TrinityPlanPatternFiring {
        if (patternIdentity == null || variantOrdinal < 0 || count == null || count.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity pattern firing requires identity, variant and positive count");
        }
    }
}
