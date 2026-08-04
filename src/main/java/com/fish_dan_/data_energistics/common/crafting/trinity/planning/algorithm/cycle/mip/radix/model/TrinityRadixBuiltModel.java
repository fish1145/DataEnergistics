package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixLinearEncoder;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixVariable;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;
import org.ojalgo.optimisation.Variable;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Couples one assembled ojAlgo model with the logical axes needed for exact decoding and objective search.
 *
 * @param model               radix encoder containing the complete copied solver model
 * @param firingVariables     stable firing axes
 * @param seedVariables       internal reserve axes
 * @param externalVariables   boundary reserve axes
 * @param objective           logical objective for the current pass
 * @param minimize            whether the objective is minimised rather than maximised
 * @param objectiveLowerBound exact certified lower bound
 * @param objectiveUpperBound exact certified upper bound
 */
public record TrinityRadixBuiltModel(
                                     TrinityRadixLinearEncoder model,
                                     Map<TrinityPatternVariant, TrinityRadixVariable> firingVariables,
                                     Map<AEKey, TrinityRadixVariable> seedVariables,
                                     Map<AEKey, TrinityRadixVariable> externalVariables,
                                     TrinityRadixVariable objective,
                                     boolean minimize,
                                     BigInteger objectiveLowerBound,
                                     BigInteger objectiveUpperBound) {

    /**
     * Reconstructs all published logical values from solver digits using exact {@link BigInteger} arithmetic.
     */
    public TrinityRadixSolvedModel decode(Map<Variable, BigInteger> values) {
        LinkedHashMap<TrinityPatternVariant, BigInteger> firings = new LinkedHashMap<>();
        firingVariables.forEach((variant, variable) -> putPositive(firings, variant, variable.decode(values)));
        return new TrinityRadixSolvedModel(
                Collections.unmodifiableMap(firings),
                decodePositive(seedVariables, values),
                decodePositive(externalVariables, values));
    }

    private static Map<AEKey, BigInteger> decodePositive(
                                                         Map<AEKey, TrinityRadixVariable> variables,
                                                         Map<Variable, BigInteger> values) {
        LinkedHashMap<AEKey, BigInteger> decoded = new LinkedHashMap<>();
        variables.forEach((key, variable) -> putPositive(decoded, key, variable.decode(values)));
        return Collections.unmodifiableMap(decoded);
    }

    private static <K> void putPositive(Map<K, BigInteger> target, K key, BigInteger value) {
        if (value.signum() > 0) {
            target.put(key, value);
        }
    }
}
