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
 * Recomputes every solver balance and bound with BigInteger before one result can constrain the next pass.
 * <p>
 * Exact rejection boundary for integral-looking ojAlgo results that violate a model equation or bound.
 */
public final class TrinityExactConservationVerifier {

    /**
     * @return stateless exact verifier
     */
    public static TrinityExactConservationVerifier create() {
        return new TrinityExactConservationVerifier();
    }

    /**
     * Adapts the legacy single-target net constraint to the generalized map contract.
     *
     * @param variants          complete model transition set
     * @param firings           exact non-zero firing values
     * @param initialInputs     exact seed and external values
     * @param upperBounds       finite input upper bounds
     * @param finalLowerBounds  required final balance per key
     * @param target            requested productive key
     * @param requiredTargetNet required net target effect independent of seed
     * @return recomputed signed net change or {@code MIP_INEXACT_RESULT}
     */
    public TrinityAlgorithmResult<Map<AEKey, BigInteger>> verify(
                                                                 List<TrinityPatternVariant> variants,
                                                                 Map<TrinityPatternVariant, BigInteger> firings,
                                                                 Map<AEKey, BigInteger> initialInputs,
                                                                 Map<AEKey, BigInteger> upperBounds,
                                                                 Map<AEKey, BigInteger> finalLowerBounds,
                                                                 AEKey target,
                                                                 BigInteger requiredTargetNet) {
        if (target == null || requiredTargetNet == null || requiredTargetNet.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity conservation target net must be positive");
        }
        return verify(
                variants,
                firings,
                initialInputs,
                upperBounds,
                finalLowerBounds,
                Map.of(target, requiredTargetNet));
    }

    /**
     * @param variants                     complete model transition set
     * @param firings                      exact non-zero firing values
     * @param initialInputs                exact seed and external values
     * @param upperBounds                  finite input upper bounds; absent keys are intentionally
     *                                     unbounded/upstream-craftable
     * @param finalLowerBounds             required final balance per key
     * @param requiredNetChangeLowerBounds required net effects independent of seed
     * @return recomputed signed net change or {@code MIP_INEXACT_RESULT}
     */
    public TrinityAlgorithmResult<Map<AEKey, BigInteger>> verify(
                                                                 List<TrinityPatternVariant> variants,
                                                                 Map<TrinityPatternVariant, BigInteger> firings,
                                                                 Map<AEKey, BigInteger> initialInputs,
                                                                 Map<AEKey, BigInteger> upperBounds,
                                                                 Map<AEKey, BigInteger> finalLowerBounds,
                                                                 Map<AEKey, BigInteger> requiredNetChangeLowerBounds) {
        if (variants == null || firings == null || initialInputs == null || upperBounds == null ||
                finalLowerBounds == null || requiredNetChangeLowerBounds == null ||
                requiredNetChangeLowerBounds.isEmpty()) {
            throw new IllegalArgumentException(
                    "A Trinity conservation verification requires complete inputs and positive net bounds");
        }

        LinkedHashSet<TrinityPatternVariant> legalVariants = new LinkedHashSet<>(variants);
        if (legalVariants.contains(null)) {
            throw new IllegalArgumentException("A Trinity conservation model cannot contain a null variant");
        }
        validatePositiveBounds(finalLowerBounds, "final");
        validatePositiveBounds(requiredNetChangeLowerBounds, "net change");
        LinkedHashMap<AEKey, BigInteger> net = new LinkedHashMap<>();
        for (Map.Entry<TrinityPatternVariant, BigInteger> firing : firings.entrySet()) {
            if (!legalVariants.contains(firing.getKey()) || firing.getValue() == null ||
                    firing.getValue().signum() <= 0) {
                return inexact("firing_domain", firing.getKey() == null ? "null" : firing.getKey().toString());
            }
            firing.getKey().netChange().forEach((key, amount) -> net.merge(key, amount.multiply(firing.getValue()), BigInteger::add));
        }
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        for (Map.Entry<AEKey, BigInteger> bound : requiredNetChangeLowerBounds.entrySet()) {
            BigInteger actual = net.getOrDefault(bound.getKey(), BigInteger.ZERO);
            if (actual.compareTo(bound.getValue()) < 0) {
                return inexact("required_net", bound.getKey() + ":" + actual + "<" + bound.getValue());
            }
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
        keys.addAll(requiredNetChangeLowerBounds.keySet());
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

    private static void validatePositiveBounds(Map<AEKey, BigInteger> bounds, String description) {
        bounds.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("A Trinity exact " + description + " bound must be positive");
            }
        });
    }

    private static <T> TrinityAlgorithmResult<T> inexact(String constraint, String value) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT,
                Component.translatable("gui.data_energistics.trinity_planning.diagnostic.inexact_result"),
                Map.of("constraint", constraint, "value", value)));
    }
}
