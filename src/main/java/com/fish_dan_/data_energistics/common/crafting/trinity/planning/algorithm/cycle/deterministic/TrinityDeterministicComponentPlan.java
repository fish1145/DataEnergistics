package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.macro.TrinityCycleMacro;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Exact component-wide result for a unique-producer SCC whose residual dependencies are acyclic.
 *
 * @param firings       aggregate firing vector for the productive cycle and every demanded residual output
 * @param minimumSeed   exact maximum prefix deficit for the selected compressed order
 * @param initialInputs exact reserved inputs after applying final-balance lower bounds
 * @param netChange     exact signed effect of the aggregate firing vector
 * @param schedule      executable compressed proof of the complete component plan
 * @param prefixOrder   one-time residual firings executed before the primitive repeat block
 * @param macro         primitive repeat proof retained independently from one-time residual firings
 * @param suffixOrder   one-time residual firings executed after every complete repeat
 */
public record TrinityDeterministicComponentPlan(
                                                Map<TrinityPatternVariant, BigInteger> firings,
                                                Map<AEKey, BigInteger> minimumSeed,
                                                Map<AEKey, BigInteger> initialInputs,
                                                Map<AEKey, BigInteger> netChange,
                                                TrinityCompressedSchedule schedule,
                                                List<TrinityVariantFiring> prefixOrder,
                                                Optional<TrinityCycleMacro> macro,
                                                List<TrinityVariantFiring> suffixOrder) {

    /**
     * Freezes all accounting and rejects a plan whose schedule or conservation equation differs from its vector.
     */
    public TrinityDeterministicComponentPlan {
        if (firings.isEmpty()) {
            throw new IllegalArgumentException("A deterministic Trinity component plan requires complete accounting");
        }
        firings = copyPositiveFirings(firings);
        minimumSeed = copyPositiveAmounts(minimumSeed);
        initialInputs = copyPositiveAmounts(initialInputs);
        netChange = copySignedAmounts(netChange);
        prefixOrder = List.copyOf(prefixOrder);
        suffixOrder = List.copyOf(suffixOrder);
        Map<AEKey, BigInteger> copiedInitialInputs = initialInputs;
        if (!schedule.aggregateFirings().equals(firings)) {
            throw new IllegalArgumentException("A deterministic Trinity component schedule must match its firing vector");
        }
        if (macro.isPresent()) {
            TrinityCycleMacro value = macro.orElseThrow();
            LinkedHashMap<TrinityPatternVariant, BigInteger> decomposedFirings = new LinkedHashMap<>();
            mergeFirings(decomposedFirings, prefixOrder);
            value.aggregateFirings().forEach(
                    (variant, count) -> decomposedFirings.merge(variant, count, BigInteger::add));
            mergeFirings(decomposedFirings, suffixOrder);
            if (!decomposedFirings.equals(firings)) {
                throw new IllegalArgumentException("A deterministic Trinity cycle decomposition must match its complete vector");
            }
        } else if (!prefixOrder.isEmpty() || !suffixOrder.isEmpty()) {
            throw new IllegalArgumentException("One-time Trinity residual firings require a primitive repeat proof");
        }

        LinkedHashMap<AEKey, BigInteger> calculatedNet = new LinkedHashMap<>();
        firings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> calculatedNet.merge(key, amount.multiply(count), BigInteger::add)));
        calculatedNet.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!calculatedNet.equals(netChange)) {
            throw new IllegalArgumentException("A deterministic Trinity component net change must match its vector");
        }
        minimumSeed.forEach((key, amount) -> {
            if (copiedInitialInputs.getOrDefault(key, BigInteger.ZERO).compareTo(amount) < 0) {
                throw new IllegalArgumentException("A deterministic Trinity component input must include its seed");
            }
        });

        LinkedHashMap<AEKey, BigInteger> finalBalances = new LinkedHashMap<>(initialInputs);
        netChange.forEach((key, amount) -> finalBalances.merge(key, amount, BigInteger::add));
        if (finalBalances.values().stream().anyMatch(amount -> amount.signum() < 0)) {
            throw new IllegalArgumentException("A deterministic Trinity component final balance cannot be negative");
        }
        finalBalances.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        if (!finalBalances.equals(schedule.finalBalances())) {
            throw new IllegalArgumentException("A deterministic Trinity component schedule must conserve its balances");
        }
    }

    private static void mergeFirings(
                                     Map<TrinityPatternVariant, BigInteger> target,
                                     List<TrinityVariantFiring> order) {
        order.forEach(firing -> target.merge(firing.variant(), firing.count(), BigInteger::add));
    }

    private static Map<TrinityPatternVariant, BigInteger> copyPositiveFirings(
                                                                              Map<TrinityPatternVariant, BigInteger> source) {
        LinkedHashMap<TrinityPatternVariant, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((variant, count) -> {
            if (variant == null || count == null || count.signum() <= 0) {
                throw new IllegalArgumentException("A deterministic Trinity component firing must be positive");
            }
            copied.put(variant, count);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copyPositiveAmounts(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("A deterministic Trinity component amount must be positive");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<AEKey, BigInteger> copySignedAmounts(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() == 0) {
                throw new IllegalArgumentException("A deterministic Trinity component net amount must be non-zero");
            }
            copied.put(key, amount);
        });
        return Collections.unmodifiableMap(copied);
    }
}
