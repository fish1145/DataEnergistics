package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Exact rejection boundary for integral-looking ojAlgo results that violate a model equation or bound.
 */
final class TrinityExactConservationVerifierImpl implements TrinityExactConservationVerifier {

    @Override
    public TrinityAlgorithmResult<Map<AEKey, BigInteger>> verify(
                                                                 List<TrinityPatternVariant> variants,
                                                                 Map<TrinityPatternVariant, BigInteger> firings,
                                                                 Map<AEKey, BigInteger> initialInputs,
                                                                 Map<AEKey, BigInteger> upperBounds,
                                                                 Map<AEKey, BigInteger> finalLowerBounds,
                                                                 AEKey target,
                                                                 BigInteger requiredTargetNet) {
        if (variants == null || firings == null || initialInputs == null || upperBounds == null ||
                finalLowerBounds == null || target == null || requiredTargetNet == null ||
                requiredTargetNet.signum() <= 0) {
            throw new IllegalArgumentException(
                    "A Trinity conservation verification requires complete inputs and a positive target net");
        }

        LinkedHashSet<TrinityPatternVariant> legalVariants = new LinkedHashSet<>(variants);
        if (legalVariants.contains(null)) {
            throw new IllegalArgumentException("A Trinity conservation model cannot contain a null variant");
        }
        finalLowerBounds.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("A Trinity exact final bound cannot be negative or null");
            }
        });
        LinkedHashMap<AEKey, BigInteger> net = new LinkedHashMap<>();
        for (Map.Entry<TrinityPatternVariant, BigInteger> firing : firings.entrySet()) {
            if (!legalVariants.contains(firing.getKey()) || firing.getValue() == null ||
                    firing.getValue().signum() <= 0) {
                return inexact("firing_domain", firing.getKey() == null ? "null" : firing.getKey().toString());
            }
            firing.getKey().netChange().forEach((key, amount) -> net.merge(key, amount.multiply(firing.getValue()), BigInteger::add));
        }
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (net.getOrDefault(target, BigInteger.ZERO).compareTo(requiredTargetNet) < 0) {
            return inexact("target_net", net.getOrDefault(target, BigInteger.ZERO).toString());
        }

        for (Map.Entry<AEKey, BigInteger> input : initialInputs.entrySet()) {
            if (input.getKey() == null || input.getValue() == null || input.getValue().signum() < 0) {
                return inexact("input_domain", String.valueOf(input.getValue()));
            }
            BigInteger upper = upperBounds.get(input.getKey());
            if (upper != null && input.getValue().compareTo(upper) > 0) {
                return inexact("input_upper", input.getValue() + ">" + upper);
            }
        }
        for (Map.Entry<AEKey, BigInteger> bound : upperBounds.entrySet()) {
            if (bound.getKey() == null || bound.getValue() == null || bound.getValue().signum() < 0) {
                throw new IllegalArgumentException("A Trinity exact upper bound cannot be negative or null");
            }
        }

        LinkedHashSet<AEKey> keys = new LinkedHashSet<>(net.keySet());
        keys.addAll(initialInputs.keySet());
        keys.addAll(finalLowerBounds.keySet());
        for (AEKey key : keys) {
            BigInteger finalBalance = initialInputs.getOrDefault(key, BigInteger.ZERO)
                    .add(net.getOrDefault(key, BigInteger.ZERO));
            BigInteger lower = finalLowerBounds.getOrDefault(key, BigInteger.ZERO);
            if (finalBalance.compareTo(lower) < 0) {
                return inexact("conservation", key + ":" + finalBalance + "<" + lower);
            }
        }
        return TrinityAlgorithmResult.success(Collections.unmodifiableMap(net));
    }

    private static <T> TrinityAlgorithmResult<T> inexact(String constraint, String value) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                Component.literal("An integral ojAlgo result violates exact Trinity constraints"),
                Map.of("constraint", constraint, "value", value)));
    }
}
