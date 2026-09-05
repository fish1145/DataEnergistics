package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import net.minecraft.network.chat.Component;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Validates a firing vector using maximum-safe batches and relevant balance breakpoints.
 * <p>
 * Bounded depth-first search over compressed firing vectors and exact balances.
 */
public final class TrinityCompressedScheduler {

    /**
     * @return stateless exact scheduler
     */
    public static TrinityCompressedScheduler create() {
        return new TrinityCompressedScheduler();
    }

    private static final String CANCELLED_KEY = "gui.data_energistics.trinity_planning.diagnostic.cancelled";
    private static final String SEARCH_LIMIT_KEY = "gui.data_energistics.trinity_planning.diagnostic.search_limit";
    private static final String NO_EXECUTABLE_ORDER_KEY = "gui.data_energistics.trinity_planning.diagnostic.no_executable_order";

    /**
     * @param firings         complete positive firing vector
     * @param initialBalances exact seed and external inputs owned before the first firing
     * @param maxStates       positive compressed search-state limit
     * @param control         cancellation and deadline boundary
     * @return executable compressed order or stable rejection
     */
    public TrinityAlgorithmResult<TrinityCompressedSchedule> schedule(
                                                                      Map<TrinityPatternVariant, BigInteger> firings,
                                                                      Map<AEKey, BigInteger> initialBalances,
                                                                      int maxStates,
                                                                      TrinityPlanningControl control) {
        if (firings == null || firings.isEmpty() || initialBalances == null || maxStates <= 0 ||
                control == null) {
            throw new IllegalArgumentException("A Trinity schedule requires complete inputs and a positive state limit");
        }

        List<TrinityPatternVariant> variants = firings.keySet().stream().sorted().toList();
        List<BigInteger> remaining = variants.stream()
                .map(variant -> requirePositive(firings.get(variant)))
                .toList();
        List<AEKey> keys = relevantKeys(variants, initialBalances);
        List<BigInteger> balances = keys.stream()
                .map(key -> requireNonNegative(initialBalances.getOrDefault(key, BigInteger.ZERO)))
                .toList();

        ArrayDeque<SearchNode> pending = new ArrayDeque<>();
        pending.push(new SearchNode(remaining, balances, List.of()));
        HashSet<StateKey> visited = new HashSet<>();
        int statesVisited = 0;
        while (!pending.isEmpty()) {
            if (control.cancellationRequested()) {
                return failure(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        CANCELLED_KEY,
                        Map.of("states", Integer.toString(statesVisited)));
            }
            if (control.deadlineExceeded()) {
                return failure(
                        TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                        SEARCH_LIMIT_KEY,
                        Map.of("reason", "timeout", "states", Integer.toString(statesVisited)));
            }

            SearchNode node = pending.pop();
            StateKey stateKey = new StateKey(node.remaining(), node.balances());
            if (!visited.add(stateKey)) {
                continue;
            }
            statesVisited = Math.addExact(statesVisited, 1);
            if (allComplete(node.remaining())) {
                return TrinityAlgorithmResult.success(new TrinityCompressedSchedule(
                        node.batches(),
                        positiveBalances(keys, node.balances()),
                        statesVisited));
            }
            if (statesVisited >= maxStates) {
                return failure(
                        TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                        SEARCH_LIMIT_KEY,
                        Map.of(
                                "limit", Integer.toString(maxStates),
                                "states", Integer.toString(statesVisited)));
            }

            List<SearchNode> successors = successors(variants, keys, node);
            for (int index = successors.size() - 1; index >= 0; index--) {
                pending.push(successors.get(index));
            }
        }
        return failure(
                TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                NO_EXECUTABLE_ORDER_KEY,
                Map.of("states", Integer.toString(statesVisited)));
    }

    private static List<SearchNode> successors(
                                               List<TrinityPatternVariant> variants,
                                               List<AEKey> keys,
                                               SearchNode node) {
        ArrayList<SearchNode> successors = new ArrayList<>();
        for (int variantIndex = 0; variantIndex < variants.size(); variantIndex++) {
            BigInteger remaining = node.remaining().get(variantIndex);
            if (remaining.signum() == 0) {
                continue;
            }
            TrinityPatternVariant variant = variants.get(variantIndex);
            BigInteger maximum = maximumSafeBatch(variant, remaining, keys, node.balances());
            if (maximum.signum() == 0) {
                continue;
            }
            for (BigInteger batch : batchCandidates(variants, variant, maximum, keys, node.balances())) {
                ArrayList<BigInteger> nextRemaining = new ArrayList<>(node.remaining());
                nextRemaining.set(variantIndex, remaining.subtract(batch));
                ArrayList<BigInteger> nextBalances = new ArrayList<>(node.balances());
                for (int keyIndex = 0; keyIndex < keys.size(); keyIndex++) {
                    BigInteger delta = variant.netChange().getOrDefault(keys.get(keyIndex), BigInteger.ZERO);
                    BigInteger updated = nextBalances.get(keyIndex).add(delta.multiply(batch));
                    if (updated.signum() < 0) {
                        throw new IllegalStateException("A maximum-safe Trinity batch produced a negative balance");
                    }
                    nextBalances.set(keyIndex, updated);
                }
                ArrayList<TrinityVariantFiring> nextBatches = new ArrayList<>(node.batches());
                nextBatches.add(new TrinityVariantFiring(variant, batch));
                successors.add(new SearchNode(
                        List.copyOf(nextRemaining),
                        List.copyOf(nextBalances),
                        List.copyOf(nextBatches)));
            }
        }
        return successors;
    }

    static BigInteger maximumSafeBatch(TrinityPatternVariant variant,
                                       BigInteger remaining,
                                       List<AEKey> keys,
                                       List<BigInteger> balances) {
        BigInteger safe = remaining;
        for (Map.Entry<AEKey, BigInteger> input : variant.inputs().entrySet()) {
            int keyIndex = keys.indexOf(input.getKey());
            BigInteger balance = balances.get(keyIndex);
            BigInteger consumption = input.getValue();
            if (balance.compareTo(consumption) < 0) {
                return BigInteger.ZERO;
            }
            BigInteger delta = variant.netChange().getOrDefault(input.getKey(), BigInteger.ZERO);
            if (delta.signum() < 0) {
                BigInteger keySafe = balance.subtract(consumption)
                        .divide(delta.negate())
                        .add(BigInteger.ONE);
                safe = safe.min(keySafe);
            }
        }
        return safe;
    }

    static List<BigInteger> batchCandidates(
                                            List<TrinityPatternVariant> variants,
                                            TrinityPatternVariant firing,
                                            BigInteger maximum,
                                            List<AEKey> keys,
                                            List<BigInteger> balances) {
        TreeSet<BigInteger> candidates = new TreeSet<>(Comparator.reverseOrder());
        candidates.add(maximum);
        if (maximum.compareTo(BigInteger.ONE) > 0) {
            candidates.add(BigInteger.ONE);
        }
        for (TrinityPatternVariant waiting : variants) {
            if (waiting.equals(firing)) {
                continue;
            }
            BigInteger breakpoint = BigInteger.ZERO;
            boolean reachable = true;
            for (Map.Entry<AEKey, BigInteger> input : waiting.inputs().entrySet()) {
                int keyIndex = keys.indexOf(input.getKey());
                BigInteger shortage = input.getValue().subtract(balances.get(keyIndex));
                if (shortage.signum() <= 0) {
                    continue;
                }
                BigInteger growth = firing.netChange().getOrDefault(input.getKey(), BigInteger.ZERO);
                if (growth.signum() <= 0) {
                    reachable = false;
                    break;
                }
                breakpoint = breakpoint.max(ceilDivide(shortage, growth));
            }
            if (reachable && breakpoint.signum() > 0 && breakpoint.compareTo(maximum) <= 0) {
                candidates.add(breakpoint);
            }
        }
        return List.copyOf(candidates);
    }

    static List<AEKey> relevantKeys(
                                    List<TrinityPatternVariant> variants,
                                    Map<AEKey, BigInteger> initialBalances) {
        LinkedHashSet<AEKey> keys = new LinkedHashSet<>();
        variants.forEach(variant -> {
            keys.addAll(variant.inputs().keySet());
            keys.addAll(variant.outputs().keySet());
        });
        keys.addAll(initialBalances.keySet());
        return List.copyOf(keys);
    }

    static Map<AEKey, BigInteger> positiveBalances(List<AEKey> keys, List<BigInteger> balances) {
        LinkedHashMap<AEKey, BigInteger> positive = new LinkedHashMap<>();
        for (int index = 0; index < keys.size(); index++) {
            if (balances.get(index).signum() > 0) {
                positive.put(keys.get(index), balances.get(index));
            }
        }
        return Collections.unmodifiableMap(positive);
    }

    static boolean allComplete(List<BigInteger> remaining) {
        return remaining.stream().allMatch(amount -> amount.signum() == 0);
    }

    private static BigInteger ceilDivide(BigInteger numerator, BigInteger denominator) {
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }

    private static BigInteger requirePositive(BigInteger value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity firing count must be positive");
        }
        return value;
    }

    private static BigInteger requireNonNegative(BigInteger value) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("A Trinity initial balance cannot be negative");
        }
        return value;
    }

    private static <T> TrinityAlgorithmResult<T> failure(
                                                         TrinityPlanningDiagnosticCode code,
                                                         String translationKey,
                                                         Map<String, String> metadata) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                code,
                Component.translatable(translationKey),
                metadata));
    }

    private record SearchNode(
                              List<BigInteger> remaining,
                              List<BigInteger> balances,
                              List<TrinityVariantFiring> batches) {}

    private record StateKey(List<BigInteger> remaining, List<BigInteger> balances) {}
}
