package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;

/**
 * One compact pattern firing vector entry inside a stage.
 *
 * @param patternIdentity  stable published pattern semantics
 * @param primaryOutput    primary output used for server-thread provider lookup
 * @param variantOrdinal   deterministic legal input-binding ordinal
 * @param count            logical firing count without per-request expansion
 * @param inputs           exact selected pattern inputs per logical firing
 * @param outputs          exact pattern-declared outputs per logical firing
 * @param remainingOutputs exact returned input containers per logical firing, separate from declared outputs
 */
public record TrinityPlanPatternFiring(
                                       TrinityPatternIdentity patternIdentity,
                                       AEKey primaryOutput,
                                       int variantOrdinal,
                                       BigInteger count,
                                       Map<AEKey, BigInteger> inputs,
                                       Map<AEKey, BigInteger> outputs,
                                       Map<AEKey, BigInteger> remainingOutputs) {

    /** Compatibility constructor for firings whose outputs have no returned input containers. */
    public TrinityPlanPatternFiring(TrinityPatternIdentity patternIdentity, AEKey primaryOutput,
                                    int variantOrdinal, BigInteger count, Map<AEKey, BigInteger> inputs,
                                    Map<AEKey, BigInteger> outputs) {
        this(patternIdentity, primaryOutput, variantOrdinal, count, inputs, outputs, Map.of());
    }

    /**
     * Rejects unbound or non-productive scheduling entries.
     */
    public TrinityPlanPatternFiring {
        if (variantOrdinal < 0 || count.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity pattern firing requires identity, variant and positive count");
        }
        inputs = TrinityPlanAmounts.validatePositive(inputs, "pattern firing input");
        outputs = TrinityPlanAmounts.validatePositive(outputs, "pattern firing output");
        remainingOutputs = TrinityPlanAmounts.validatePositive(remainingOutputs, "pattern firing remainder");
        if (!outputs.containsKey(primaryOutput)) {
            throw new IllegalArgumentException("A Trinity pattern firing must retain its primary output");
        }
    }
}
