package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;

/**
 * One compact pattern firing vector entry inside a stage.
 *
 * @param patternIdentity stable published pattern semantics
 * @param primaryOutput   primary output used for server-thread provider lookup
 * @param variantOrdinal  deterministic legal input-binding ordinal
 * @param count           logical firing count without per-request expansion
 * @param outputs         exact pattern-declared outputs per logical firing
 */
public record TrinityPlanPatternFiring(
                                       TrinityPatternIdentity patternIdentity,
                                       AEKey primaryOutput,
                                       int variantOrdinal,
                                       BigInteger count,
                                       Map<AEKey, BigInteger> outputs) {

    /**
     * Rejects unbound or non-productive scheduling entries.
     */
    public TrinityPlanPatternFiring {
        if (patternIdentity == null || primaryOutput == null || variantOrdinal < 0 ||
                count == null || count.signum() <= 0 || outputs == null) {
            throw new IllegalArgumentException("A Trinity pattern firing requires identity, variant and positive count");
        }
        outputs = TrinityPlanAmounts.copyPositive(outputs, "pattern firing output");
        if (!outputs.containsKey(primaryOutput)) {
            throw new IllegalArgumentException("A Trinity pattern firing must retain its primary output");
        }
    }

    /**
     * Creates a synthetic one-unit primary-output firing used by focused plan fixtures.
     */
    public TrinityPlanPatternFiring(TrinityPatternIdentity patternIdentity,
                                    AEKey primaryOutput,
                                    int variantOrdinal,
                                    BigInteger count) {
        this(patternIdentity, primaryOutput, variantOrdinal, count, defaultOutputs(primaryOutput));
    }

    private static Map<AEKey, BigInteger> defaultOutputs(AEKey primaryOutput) {
        if (primaryOutput == null) {
            throw new IllegalArgumentException("A Trinity pattern firing requires a primary output");
        }
        return Map.of(primaryOutput, BigInteger.ONE);
    }
}
