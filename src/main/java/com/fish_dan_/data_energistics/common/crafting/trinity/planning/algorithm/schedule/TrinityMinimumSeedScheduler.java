package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Finds the minimum exact internal seed for a fixed MIP firing vector using bounded compressed scheduling.
 * <p>
 * Dijkstra-style seed search: ordinary batches cost zero and exact seed injections cost their added item units.
 */
public final class TrinityMinimumSeedScheduler {

    /**
     * @return stateless bounded scheduler
     */
    public static TrinityMinimumSeedScheduler create() {
        return new TrinityMinimumSeedScheduler();
    }

    private static final String CANCELLED_KEY = "gui.data_energistics.trinity_planning.diagnostic.cancelled";
    private static final String SEARCH_LIMIT_KEY = "gui.data_energistics.trinity_planning.diagnostic.search_limit";
    private static final String NO_EXECUTABLE_ORDER_KEY = "gui.data_energistics.trinity_planning.diagnostic.no_executable_order";

    /**
     * @param firings       positive MIP firing vector
     * @param externalKeys  boundary keys charged by the first lexicographic objective
     * @param seedableKeys  internal SCC keys charged by the second lexicographic objective
     * @param minimumInputs conservation-required balances injected before schedule search
     * @param maximumInputs stock or upstream-production upper bound for each injected key
     * @param maxStates     compressed state limit
     * @param control       cancellation and deadline boundary
     * @return minimum seed and executable schedule, or stable rejection
     */
    public TrinityAlgorithmResult<TrinityMinimumSeedSchedule> find(
                                                                   Map<TrinityPatternVariant, BigInteger> firings,
                                                                   Set<AEKey> externalKeys,
                                                                   Set<AEKey> seedableKeys,
                                                                   Map<AEKey, BigInteger> minimumInputs,
                                                                   Map<AEKey, BigInteger> maximumInputs,
                                                                   int maxStates,
                                                                   TrinityPlanningControl control) {
        return search(
                firings,
                externalKeys,
                seedableKeys,
                minimumInputs,
                maximumInputs,
                null,
                false,
                maxStates,
                control);
    }

    /**
     * Finds the minimum true prefix seed for a fixed firing vector after the global external objective has been fixed.
     * External injections may use any exact distribution whose total does not exceed {@code externalTotal}; unused
     * units can then be added without affecting executability.
     *
     * @param externalTotal exact global external-input budget
     */
    public TrinityAlgorithmResult<TrinityMinimumSeedSchedule> findWithinExternalTotal(
                                                                                      Map<TrinityPatternVariant, BigInteger> firings,
                                                                                      Set<AEKey> externalKeys,
                                                                                      Set<AEKey> seedableKeys,
                                                                                      Map<AEKey, BigInteger> minimumInputs,
                                                                                      Map<AEKey, BigInteger> maximumInputs,
                                                                                      BigInteger externalTotal,
                                                                                      int maxStates,
                                                                                      TrinityPlanningControl control) {
        if (externalTotal == null || externalTotal.signum() < 0) {
            throw new IllegalArgumentException("A Trinity fixed external total cannot be negative or null");
        }
        return search(
                firings,
                externalKeys,
                seedableKeys,
                minimumInputs,
                maximumInputs,
                externalTotal,
                true,
                maxStates,
                control);
    }

    private TrinityAlgorithmResult<TrinityMinimumSeedSchedule> search(
                                                                      Map<TrinityPatternVariant, BigInteger> firings,
                                                                      Set<AEKey> externalKeys,
                                                                      Set<AEKey> seedableKeys,
                                                                      Map<AEKey, BigInteger> minimumInputs,
                                                                      Map<AEKey, BigInteger> maximumInputs,
                                                                      BigInteger externalLimit,
                                                                      boolean seedFirst,
                                                                      int maxStates,
                                                                      TrinityPlanningControl control) {
        if (firings == null || firings.isEmpty() || externalKeys == null || seedableKeys == null ||
                minimumInputs == null || maximumInputs == null || maxStates <= 0 || control == null) {
            throw new IllegalArgumentException(
                    "A Trinity seed search requires complete inputs and a positive state limit");
        }

        List<TrinityPatternVariant> variants = firings.keySet().stream().sorted().toList();
        List<BigInteger> remaining = variants.stream().map(variant -> requirePositive(firings.get(variant))).toList();
        if (!Collections.disjoint(externalKeys, seedableKeys)) {
            throw new IllegalArgumentException("A Trinity input key cannot be both external and internal seed");
        }
        LinkedHashSet<AEKey> allBalanceKeys = new LinkedHashSet<>(externalKeys);
        allBalanceKeys.addAll(seedableKeys);
        allBalanceKeys.addAll(minimumInputs.keySet());
        allBalanceKeys.addAll(maximumInputs.keySet());
        List<AEKey> keys = TrinityCompressedScheduler.relevantKeys(variants, toZeroMap(allBalanceKeys));
        validateInputBounds(externalKeys, seedableKeys, minimumInputs, maximumInputs);
        List<BigInteger> balances = keys.stream()
                .map(key -> minimumInputs.getOrDefault(key, BigInteger.ZERO))
                .toList();
        List<BigInteger> external = categoryVector(keys, minimumInputs, externalKeys);
        List<BigInteger> seed = categoryVector(keys, minimumInputs, seedableKeys);
        BigInteger externalUnits = external.stream().reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger seedUnits = seed.stream().reduce(BigInteger.ZERO, BigInteger::add);
        if (externalLimit != null && externalUnits.compareTo(externalLimit) > 0) {
            return failure(
                    TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                    NO_EXECUTABLE_ORDER_KEY,
                    Map.of("states", "0"));
        }

        Comparator<SearchNode> ordering = seedFirst ? Comparator
                .comparing(SearchNode::seedUnits)
                .thenComparing(SearchNode::externalUnits) :
                Comparator
                        .comparing(SearchNode::externalUnits)
                        .thenComparing(SearchNode::seedUnits);
        // Costs stay authoritative; within one cost layer, depth-first expansion follows compressed maximum batches.
        ordering = ordering
                .thenComparing(Comparator.comparingInt(
                        (SearchNode node) -> node.batches().size()).reversed())
                .thenComparingLong(SearchNode::sequence);
        PriorityQueue<SearchNode> pending = new PriorityQueue<>(ordering);
        long sequence = 0L;
        pending.add(new SearchNode(
                remaining,
                balances,
                external,
                seed,
                List.of(),
                externalUnits,
                seedUnits,
                sequence++));
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

            SearchNode node = pending.remove();
            if (!visited.add(new StateKey(node.remaining(), node.balances()))) {
                continue;
            }
            statesVisited = Math.addExact(statesVisited, 1);
            if (TrinityCompressedScheduler.allComplete(node.remaining())) {
                return TrinityAlgorithmResult.success(new TrinityMinimumSeedSchedule(
                        positiveVector(keys, node.external()),
                        positiveVector(keys, node.seed()),
                        new TrinityCompressedSchedule(
                                node.batches(),
                                TrinityCompressedScheduler.positiveBalances(keys, node.balances()),
                                statesVisited)));
            }
            if (statesVisited >= maxStates) {
                return failure(
                        TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                        SEARCH_LIMIT_KEY,
                        Map.of(
                                "limit", Integer.toString(maxStates),
                                "states", Integer.toString(statesVisited)));
            }

            for (int variantIndex = 0; variantIndex < variants.size(); variantIndex++) {
                BigInteger count = node.remaining().get(variantIndex);
                if (count.signum() == 0) {
                    continue;
                }
                TrinityPatternVariant variant = variants.get(variantIndex);
                BigInteger safe = TrinityCompressedScheduler.maximumSafeBatch(
                        variant,
                        count,
                        keys,
                        node.balances());
                if (safe.signum() > 0) {
                    sequence = enqueueBatches(
                            pending,
                            sequence,
                            variants,
                            keys,
                            node,
                            variantIndex,
                            safe,
                            node.balances(),
                            node.external(),
                            node.seed(),
                            node.externalUnits(),
                            node.seedUnits());
                    continue;
                }

                Optional<SeedInjection> injection = requiredInjection(
                        variant,
                        keys,
                        node.balances(),
                        node.external(),
                        node.seed(),
                        externalKeys,
                        seedableKeys,
                        maximumInputs);
                if (injection.isEmpty()) {
                    continue;
                }
                SeedInjection required = injection.orElseThrow();
                if (externalLimit != null && required.externalUnits().compareTo(externalLimit) > 0) {
                    continue;
                }
                BigInteger injectedSafe = TrinityCompressedScheduler.maximumSafeBatch(
                        variant,
                        count,
                        keys,
                        required.balances());
                if (injectedSafe.signum() <= 0) {
                    throw new IllegalStateException("An exact Trinity seed injection did not enable its transition");
                }
                sequence = enqueueBatches(
                        pending,
                        sequence,
                        variants,
                        keys,
                        node,
                        variantIndex,
                        injectedSafe,
                        required.balances(),
                        required.external(),
                        required.seed(),
                        required.externalUnits(),
                        required.seedUnits());
            }
        }
        return failure(
                TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                NO_EXECUTABLE_ORDER_KEY,
                Map.of("states", Integer.toString(statesVisited)));
    }

    private static long enqueueBatches(
                                       PriorityQueue<SearchNode> pending,
                                       long nextSequence,
                                       List<TrinityPatternVariant> variants,
                                       List<AEKey> keys,
                                       SearchNode node,
                                       int variantIndex,
                                       BigInteger maximum,
                                       List<BigInteger> startingBalances,
                                       List<BigInteger> external,
                                       List<BigInteger> seed,
                                       BigInteger externalUnits,
                                       BigInteger seedUnits) {
        TrinityPatternVariant variant = variants.get(variantIndex);
        for (BigInteger batch : TrinityCompressedScheduler.batchCandidates(
                variants,
                variant,
                maximum,
                keys,
                startingBalances)) {
            ArrayList<BigInteger> remaining = new ArrayList<>(node.remaining());
            remaining.set(variantIndex, remaining.get(variantIndex).subtract(batch));
            ArrayList<BigInteger> balances = new ArrayList<>(startingBalances);
            for (int keyIndex = 0; keyIndex < keys.size(); keyIndex++) {
                BigInteger delta = variant.netChange().getOrDefault(keys.get(keyIndex), BigInteger.ZERO);
                BigInteger updated = balances.get(keyIndex).add(delta.multiply(batch));
                if (updated.signum() < 0) {
                    throw new IllegalStateException("A seeded Trinity batch produced a negative balance");
                }
                balances.set(keyIndex, updated);
            }
            ArrayList<TrinityVariantFiring> batches = new ArrayList<>(node.batches());
            batches.add(new TrinityVariantFiring(variant, batch));
            pending.add(new SearchNode(
                    List.copyOf(remaining),
                    List.copyOf(balances),
                    external,
                    seed,
                    List.copyOf(batches),
                    externalUnits,
                    seedUnits,
                    nextSequence++));
        }
        return nextSequence;
    }

    private static Optional<SeedInjection> requiredInjection(
                                                             TrinityPatternVariant variant,
                                                             List<AEKey> keys,
                                                             List<BigInteger> balances,
                                                             List<BigInteger> external,
                                                             List<BigInteger> seed,
                                                             Set<AEKey> externalKeys,
                                                             Set<AEKey> seedableKeys,
                                                             Map<AEKey, BigInteger> maximumInputs) {
        ArrayList<BigInteger> injectedBalances = new ArrayList<>(balances);
        ArrayList<BigInteger> injectedExternal = new ArrayList<>(external);
        ArrayList<BigInteger> injectedSeed = new ArrayList<>(seed);
        BigInteger addedExternal = BigInteger.ZERO;
        BigInteger addedSeed = BigInteger.ZERO;
        for (Map.Entry<AEKey, BigInteger> input : variant.inputs().entrySet()) {
            int keyIndex = keys.indexOf(input.getKey());
            BigInteger deficit = input.getValue().subtract(injectedBalances.get(keyIndex));
            if (deficit.signum() <= 0) {
                continue;
            }
            boolean externalInput = externalKeys.contains(input.getKey());
            if (!externalInput && !seedableKeys.contains(input.getKey())) {
                return Optional.empty();
            }
            List<BigInteger> category = externalInput ? injectedExternal : injectedSeed;
            BigInteger newAmount = category.get(keyIndex).add(deficit);
            if (newAmount.compareTo(maximumInputs.getOrDefault(input.getKey(), BigInteger.ZERO)) > 0) {
                return Optional.empty();
            }
            category.set(keyIndex, newAmount);
            injectedBalances.set(keyIndex, injectedBalances.get(keyIndex).add(deficit));
            if (externalInput) {
                addedExternal = addedExternal.add(deficit);
            } else {
                addedSeed = addedSeed.add(deficit);
            }
        }
        if (addedExternal.signum() == 0 && addedSeed.signum() == 0) {
            return Optional.empty();
        }
        BigInteger previousExternal = external.stream().reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger previousUnits = seed.stream().reduce(BigInteger.ZERO, BigInteger::add);
        return Optional.of(new SeedInjection(
                List.copyOf(injectedBalances),
                List.copyOf(injectedExternal),
                List.copyOf(injectedSeed),
                previousExternal.add(addedExternal),
                previousUnits.add(addedSeed)));
    }

    private static void validateInputBounds(Set<AEKey> externalKeys,
                                            Set<AEKey> seedableKeys,
                                            Map<AEKey, BigInteger> minimumInputs,
                                            Map<AEKey, BigInteger> maximumInputs) {
        for (AEKey key : externalKeys) {
            if (key == null) {
                throw new IllegalArgumentException("A Trinity external key cannot be null");
            }
        }
        for (AEKey key : seedableKeys) {
            if (key == null) {
                throw new IllegalArgumentException("A Trinity seedable key cannot be null");
            }
        }
        maximumInputs.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("A Trinity seed bound cannot be negative or null");
            }
        });
        minimumInputs.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0 ||
                    (!externalKeys.contains(key) && !seedableKeys.contains(key))) {
                throw new IllegalArgumentException("A Trinity input lower bound is invalid");
            }
            if (amount.compareTo(maximumInputs.getOrDefault(key, BigInteger.ZERO)) > 0) {
                throw new IllegalArgumentException("A Trinity input lower bound exceeds its upper bound");
            }
        });
    }

    private static Map<AEKey, BigInteger> toZeroMap(Set<AEKey> keys) {
        LinkedHashMap<AEKey, BigInteger> zero = new LinkedHashMap<>();
        keys.forEach(key -> zero.put(key, BigInteger.ZERO));
        return zero;
    }

    private static List<BigInteger> categoryVector(
                                                   List<AEKey> keys,
                                                   Map<AEKey, BigInteger> minimumInputs,
                                                   Set<AEKey> categoryKeys) {
        return keys.stream()
                .map(key -> categoryKeys.contains(key) ?
                        minimumInputs.getOrDefault(key, BigInteger.ZERO) :
                        BigInteger.ZERO)
                .toList();
    }

    private static Map<AEKey, BigInteger> positiveVector(List<AEKey> keys, List<BigInteger> values) {
        LinkedHashMap<AEKey, BigInteger> positive = new LinkedHashMap<>();
        for (int index = 0; index < keys.size(); index++) {
            if (values.get(index).signum() > 0) {
                positive.put(keys.get(index), values.get(index));
            }
        }
        return positive;
    }

    private static BigInteger requirePositive(BigInteger value) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity seed-search firing count must be positive");
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
                              List<BigInteger> external,
                              List<BigInteger> seed,
                              List<TrinityVariantFiring> batches,
                              BigInteger externalUnits,
                              BigInteger seedUnits,
                              long sequence) {}

    private record StateKey(List<BigInteger> remaining, List<BigInteger> balances) {}

    private record SeedInjection(
                                 List<BigInteger> balances,
                                 List<BigInteger> external,
                                 List<BigInteger> seed,
                                 BigInteger externalUnits,
                                 BigInteger seedUnits) {}
}
