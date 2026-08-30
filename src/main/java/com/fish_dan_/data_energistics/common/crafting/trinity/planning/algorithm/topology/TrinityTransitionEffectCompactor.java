package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Retains one stable representative for variants with exactly identical executable transition effects.
 * <p>
 * This compaction is deliberately stricter than net-change equality: primary output, exact consumption, declared
 * output and complete output including input remainders must all match.
 */
public final class TrinityTransitionEffectCompactor {

    /**
     * @return stateless exact compactor
     */
    public static TrinityTransitionEffectCompactor create() {
        return new TrinityTransitionEffectCompactor();
    }

    /**
     * Returns stable representatives in the natural variant order used by the final identity objective.
     */
    public List<TrinityPatternVariant> compact(List<TrinityPatternVariant> variants) {
        if (variants == null) {
            throw new IllegalArgumentException("Trinity transition compaction requires variants");
        }
        LinkedHashMap<TransitionEffect, TrinityPatternVariant> representatives = new LinkedHashMap<>();
        variants.stream().sorted().forEach(variant -> representatives.putIfAbsent(
                TransitionEffect.from(variant),
                variant));
        return List.copyOf(representatives.values());
    }

    private record TransitionEffect(
                                    AEKey primaryOutput,
                                    Map<AEKey, BigInteger> inputs,
                                    Map<AEKey, BigInteger> declaredOutputs,
                                    Map<AEKey, BigInteger> outputs) {

        private static TransitionEffect from(TrinityPatternVariant variant) {
            if (variant == null) {
                throw new IllegalArgumentException("A Trinity transition effect requires a variant");
            }
            return new TransitionEffect(
                    variant.primaryOutput(),
                    variant.inputs(),
                    variant.declaredOutputs(),
                    variant.outputs());
        }
    }
}
