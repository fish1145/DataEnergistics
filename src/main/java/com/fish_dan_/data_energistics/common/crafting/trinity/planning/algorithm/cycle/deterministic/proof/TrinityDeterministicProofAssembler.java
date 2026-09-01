package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.proof;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.TrinityDeterministicComponentPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability.TrinityDeterministicBasis;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.firing.TrinityDeterministicFiringSolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicDiagnostics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.support.TrinityDeterministicFiringMath;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.seed.TrinityCycleSeedRequirement;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityDeterministicRepeatScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityMinimumSeedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityMinimumSeedScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts an exact deterministic firing vector into an executable seed, prefix, repeat, and suffix proof.
 * <p>
 * Reconstructs an executable compressed schedule and verifies its exact balances without objective refinement.
 */
public final class TrinityDeterministicProofAssembler {

    /**
     * Creates the proof assembler from exact minimum-seed and repeated-cycle schedulers.
     */
    public static TrinityDeterministicProofAssembler create(
                                                            TrinityMinimumSeedScheduler seedScheduler,
                                                            TrinityDeterministicRepeatScheduler repeatScheduler) {
        return new TrinityDeterministicProofAssembler(seedScheduler, repeatScheduler);
    }

    private final TrinityMinimumSeedScheduler seedScheduler;
    private final TrinityDeterministicRepeatScheduler repeatScheduler;

    TrinityDeterministicProofAssembler(
                                       TrinityMinimumSeedScheduler seedScheduler,
                                       TrinityDeterministicRepeatScheduler repeatScheduler) {
        this.seedScheduler = seedScheduler;
        this.repeatScheduler = repeatScheduler;
    }

    /**
     * Proves exact conservation and compressed executability for the first stable constructive firing vector.
     */
    public TrinityAlgorithmResult<TrinityDeterministicComponentPlan> assemble(
                                                                              TrinityStronglyConnectedComponent component,
                                                                              TrinityCycleDemand demand,
                                                                              Map<AEKey, BigInteger> available,
                                                                              Set<AEKey> producibleInputs,
                                                                              TrinityDeterministicFiringSolution firingSolution,
                                                                              int maxStates,
                                                                              TrinityPlanningControl control) {
        if (maxStates <= 0) {
            throw new IllegalArgumentException("A deterministic proof assembly request is incomplete");
        }
        TrinityDeterministicBasis basis = firingSolution.basis();
        Map<TrinityPatternVariant, BigInteger> firings = firingSolution.firings();
        Map<AEKey, BigInteger> totalNet = firingSolution.totalNet();
        LinkedHashMap<AEKey, BigInteger> conservationInputs = conservationInputs(
                component,
                demand,
                totalNet);
        CycleDecomposition decomposition = decompose(
                basis.primitiveFirings(),
                firings,
                basis.reservoir(),
                basis.residualTopology().executionOrder());
        LinkedHashMap<AEKey, BigInteger> initialInputs = new LinkedHashMap<>(conservationInputs);
        applyRequiredPrefix(decomposition.prefixOrder(), initialInputs);
        LinkedHashMap<AEKey, BigInteger> cycleStart = simulate(
                initialInputs,
                decomposition.prefixOrder());
        Map<AEKey, BigInteger> cycleMaximum = cycleStartMaximum(
                component,
                basis.primitiveFirings(),
                available,
                producibleInputs,
                cycleStart,
                decomposition.prefixOrder());
        TrinityAlgorithmResult<NormalizedCycle> normalized = normalizePrimitiveCycle(
                component,
                basis.primitiveFirings(),
                cycleStart,
                cycleMaximum,
                maxStates,
                control);
        if (!normalized.successful()) {
            return TrinityAlgorithmResult.failure(normalized.diagnostic());
        }
        Map<AEKey, BigInteger> requiredCycleStart = normalized.value().initialBalances();
        if (decomposition.repetitions().signum() > 0) {
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> repeatedStart = new Object2ObjectLinkedOpenHashMap<>(requiredCycleStart);
            TrinityCycleSeedRequirement.repeatedMinimumInputs(
                    normalized.value().order(),
                    decomposition.repetitions()).forEach(
                            (key, amount) -> repeatedStart.merge(key, amount, BigInteger::max));
            requiredCycleStart = Object2ObjectMaps.unmodifiable(repeatedStart);
        }
        mergeRequiredCycleStart(initialInputs, cycleStart, requiredCycleStart);

        int totalStates = Math.addExact(
                normalized.value().statesVisited(),
                firingSolution.balancePasses());
        if (totalStates > maxStates) {
            return TrinityDeterministicDiagnostics.searchLimit(maxStates, totalStates);
        }
        Map<AEKey, BigInteger> minimumSeed = internalAmounts(initialInputs, component.keys());
        int remainingStates = Math.subtractExact(maxStates, totalStates);
        if (remainingStates <= 0) {
            return TrinityDeterministicDiagnostics.searchLimit(maxStates, totalStates);
        }
        TrinityAlgorithmResult<TrinityCompressedSchedule> scheduled = schedule(
                decomposition.prefixOrder(),
                normalized.value().order(),
                decomposition.repetitions(),
                decomposition.suffixOrder(),
                initialInputs,
                remainingStates,
                control);
        if (!scheduled.successful()) {
            return TrinityAlgorithmResult.failure(scheduled.diagnostic());
        }
        totalStates = Math.addExact(totalStates, scheduled.value().statesVisited());
        if (totalStates > maxStates) {
            return TrinityDeterministicDiagnostics.searchLimit(maxStates, totalStates);
        }
        TrinityCompressedSchedule completeSchedule = scheduled.value().withStatesVisited(totalStates);
        TrinityDeterministicComponentPlan plan = new TrinityDeterministicComponentPlan(
                firings,
                minimumSeed,
                initialInputs,
                totalNet,
                completeSchedule);
        return TrinityAlgorithmResult.success(plan);
    }

    private TrinityAlgorithmResult<NormalizedCycle> normalizePrimitiveCycle(
                                                                            TrinityStronglyConnectedComponent component,
                                                                            Map<TrinityPatternVariant, BigInteger> primitiveFirings,
                                                                            Map<AEKey, BigInteger> minimumBalances,
                                                                            Map<AEKey, BigInteger> maximumBalances,
                                                                            int maxStates,
                                                                            TrinityPlanningControl control) {
        Set<AEKey> internalKeys = Set.copyOf(component.keys());
        LinkedHashSet<AEKey> externalKeys = new LinkedHashSet<>();
        primitiveFirings.keySet().forEach(variant -> variant.inputs().keySet().forEach(key -> {
            if (!internalKeys.contains(key)) {
                externalKeys.add(key);
            }
        }));
        TrinityAlgorithmResult<TrinityMinimumSeedSchedule> seeded = this.seedScheduler.find(
                primitiveFirings,
                Collections.unmodifiableSet(externalKeys),
                internalKeys,
                schedulableInputBalances(minimumBalances, externalKeys, internalKeys),
                maximumBalances,
                maxStates,
                control);
        if (!seeded.successful()) {
            return TrinityAlgorithmResult.failure(seeded.diagnostic());
        }
        LinkedHashMap<AEKey, BigInteger> initialBalances = new LinkedHashMap<>(seeded.value().externalInputs());
        seeded.value().minimumSeed().forEach(
                (key, amount) -> initialBalances.merge(key, amount, BigInteger::add));
        return TrinityAlgorithmResult.success(new NormalizedCycle(
                seeded.value().schedule().batches(),
                Collections.unmodifiableMap(initialBalances),
                seeded.value().schedule().statesVisited()));
    }

    private static Map<AEKey, BigInteger> schedulableInputBalances(
                                                                   Map<AEKey, BigInteger> balances,
                                                                   Set<AEKey> externalKeys,
                                                                   Set<AEKey> internalKeys) {
        LinkedHashMap<AEKey, BigInteger> inputs = new LinkedHashMap<>();
        balances.forEach((key, amount) -> {
            if (externalKeys.contains(key) || internalKeys.contains(key)) {
                inputs.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(inputs);
    }

    private TrinityAlgorithmResult<TrinityCompressedSchedule> schedule(
                                                                       List<TrinityVariantFiring> prefixOrder,
                                                                       List<TrinityVariantFiring> baseOrder,
                                                                       BigInteger repetitions,
                                                                       List<TrinityVariantFiring> suffixOrder,
                                                                       Map<AEKey, BigInteger> initialInputs,
                                                                       int maxStates,
                                                                       TrinityPlanningControl control) {
        if (maxStates <= 0) {
            return TrinityDeterministicDiagnostics.searchLimit(0, 0);
        }
        ArrayList<TrinityVariantFiring> prefixBatches = new ArrayList<>();
        ArrayList<TrinityVariantFiring> suffixBatches = new ArrayList<>();
        List<TrinityVariantFiring> repeatUnit = List.of();
        BigInteger repeatCount = BigInteger.ZERO;
        LinkedHashMap<AEKey, BigInteger> balances = new LinkedHashMap<>(initialInputs);
        int states = 1;
        for (TrinityVariantFiring firing : prefixOrder) {
            TrinityAlgorithmResult<Integer> executed = executeBatch(
                    firing,
                    balances,
                    prefixBatches,
                    states,
                    maxStates,
                    control);
            if (!executed.successful()) {
                return TrinityAlgorithmResult.failure(executed.diagnostic());
            }
            states = executed.value();
        }
        if (repetitions.signum() > 0) {
            int remainingStates = Math.subtractExact(maxStates, states);
            if (remainingStates <= 0) {
                return TrinityDeterministicDiagnostics.searchLimit(maxStates, states);
            }
            TrinityAlgorithmResult<TrinityCompressedSchedule> repeated = this.repeatScheduler.schedule(
                    baseOrder,
                    repetitions,
                    positiveBalances(balances),
                    remainingStates,
                    control);
            if (!repeated.successful()) {
                return repeated;
            }
            if (!repeated.value().hasRepeatBlock()) {
                throw new IllegalStateException("A deterministic repeat scheduler returned a flat proof");
            }
            repeatUnit = repeated.value().repeatUnit();
            repeatCount = repeated.value().repeatCount();
            balances.clear();
            balances.putAll(repeated.value().finalBalances());
            states = Math.addExact(states, repeated.value().statesVisited());
        }
        for (TrinityVariantFiring firing : suffixOrder) {
            TrinityAlgorithmResult<Integer> executed = executeBatch(
                    firing,
                    balances,
                    suffixBatches,
                    states,
                    maxStates,
                    control);
            if (!executed.successful()) {
                return TrinityAlgorithmResult.failure(executed.diagnostic());
            }
            states = executed.value();
        }
        Map<AEKey, BigInteger> finalBalances = positiveBalances(balances);
        if (repeatCount.signum() > 0) {
            return TrinityAlgorithmResult.success(TrinityCompressedSchedule.repeated(
                    prefixBatches,
                    repeatUnit,
                    repeatCount,
                    suffixBatches,
                    finalBalances,
                    states));
        }
        prefixBatches.addAll(suffixBatches);
        return TrinityAlgorithmResult.success(new TrinityCompressedSchedule(prefixBatches, finalBalances, states));
    }

    private static TrinityAlgorithmResult<Integer> executeBatch(
                                                                TrinityVariantFiring firing,
                                                                Map<AEKey, BigInteger> balances,
                                                                List<TrinityVariantFiring> batches,
                                                                int states,
                                                                int maxStates,
                                                                TrinityPlanningControl control) {
        TrinityDeterministicDiagnostics.StopState state = TrinityDeterministicDiagnostics.stopState(control);
        if (state != TrinityDeterministicDiagnostics.StopState.RUNNING) {
            return TrinityDeterministicDiagnostics.stopped(state);
        }
        if (states >= maxStates) {
            return TrinityDeterministicDiagnostics.searchLimit(maxStates, states);
        }
        if (lacksInputs(balances, requiredAtStart(firing))) {
            return TrinityDeterministicDiagnostics.failure(
                    TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                    TrinityDeterministicDiagnostics.NO_EXECUTABLE_ORDER_KEY,
                    Map.of("variant", firing.variant().patternIdentity().publicationEncoding()));
        }
        apply(firing, balances);
        appendBatch(batches, firing);
        return TrinityAlgorithmResult.success(Math.incrementExact(states));
    }

    private static CycleDecomposition decompose(
                                                Map<TrinityPatternVariant, BigInteger> primitiveFirings,
                                                Map<TrinityPatternVariant, BigInteger> firings,
                                                AEKey reservoir,
                                                List<TrinityPatternVariant> topologicalOrder) {
        BigInteger repetitions = null;
        for (Map.Entry<TrinityPatternVariant, BigInteger> primitive : primitiveFirings.entrySet()) {
            BigInteger available = firings.getOrDefault(
                    primitive.getKey(),
                    TrinityDeterministicFiringMath.ZERO);
            BigInteger supported = available.divide(primitive.getValue());
            repetitions = repetitions == null ? supported : repetitions.min(supported);
        }
        if (repetitions == null) {
            throw new IllegalStateException("A deterministic Trinity basis cannot be empty");
        }
        LinkedHashMap<TrinityPatternVariant, BigInteger> residual = new LinkedHashMap<>();
        for (TrinityPatternVariant variant : topologicalOrder) {
            BigInteger count = firings.getOrDefault(variant, TrinityDeterministicFiringMath.ZERO)
                    .subtract(primitiveFirings
                            .getOrDefault(variant, TrinityDeterministicFiringMath.ZERO)
                            .multiply(repetitions));
            if (count.signum() < 0) {
                throw new IllegalStateException("A shifted Trinity vector cannot underflow its primitive decomposition");
            }
            if (count.signum() > 0) {
                residual.put(variant, count);
            }
        }
        ArrayList<TrinityVariantFiring> prefix = new ArrayList<>();
        ArrayList<TrinityVariantFiring> suffix = new ArrayList<>();
        residual.forEach((variant, count) -> {
            TrinityVariantFiring firing = new TrinityVariantFiring(variant, count);
            if (variant.netChange().getOrDefault(reservoir, TrinityDeterministicFiringMath.ZERO).signum() > 0) {
                prefix.add(firing);
            } else {
                suffix.add(firing);
            }
        });
        return new CycleDecomposition(
                repetitions,
                List.copyOf(prefix),
                List.copyOf(suffix));
    }

    private static LinkedHashMap<AEKey, BigInteger> conservationInputs(
                                                                       TrinityStronglyConnectedComponent component,
                                                                       TrinityCycleDemand demand,
                                                                       Map<AEKey, BigInteger> netChange) {
        LinkedHashSet<AEKey> keys = new LinkedHashSet<>();
        component.cycleVariants().forEach(variant -> keys.addAll(variant.netChange().keySet()));
        keys.addAll(demand.finalBalanceLowerBounds().keySet());
        LinkedHashMap<AEKey, BigInteger> inputs = new LinkedHashMap<>();
        for (AEKey key : keys) {
            BigInteger required = demand.finalBalanceLowerBounds()
                    .getOrDefault(key, TrinityDeterministicFiringMath.ZERO)
                    .subtract(netChange.getOrDefault(key, TrinityDeterministicFiringMath.ZERO))
                    .max(TrinityDeterministicFiringMath.ZERO);
            if (required.signum() > 0) {
                inputs.put(key, required);
            }
        }
        return inputs;
    }

    private static void applyRequiredPrefix(
                                            List<TrinityVariantFiring> prefix,
                                            Map<AEKey, BigInteger> initialInputs) {
        LinkedHashMap<AEKey, BigInteger> balances = new LinkedHashMap<>(initialInputs);
        for (TrinityVariantFiring firing : prefix) {
            requiredAtStart(firing).forEach((key, required) -> {
                BigInteger deficit = required.subtract(
                        balances.getOrDefault(key, TrinityDeterministicFiringMath.ZERO));
                if (deficit.signum() > 0) {
                    initialInputs.merge(key, deficit, BigInteger::add);
                    balances.merge(key, deficit, BigInteger::add);
                }
            });
            apply(firing, balances);
        }
    }

    private static LinkedHashMap<AEKey, BigInteger> simulate(
                                                             Map<AEKey, BigInteger> initial,
                                                             List<TrinityVariantFiring> order) {
        LinkedHashMap<AEKey, BigInteger> balances = new LinkedHashMap<>(initial);
        for (TrinityVariantFiring firing : order) {
            if (lacksInputs(balances, requiredAtStart(firing))) {
                throw new IllegalStateException("A derived Trinity prefix is not executable from its reserved inputs");
            }
            apply(firing, balances);
        }
        return balances;
    }

    private static Map<AEKey, BigInteger> cycleStartMaximum(
                                                            TrinityStronglyConnectedComponent component,
                                                            Map<TrinityPatternVariant, BigInteger> primitiveFirings,
                                                            Map<AEKey, BigInteger> available,
                                                            Set<AEKey> producibleInputs,
                                                            Map<AEKey, BigInteger> cycleStart,
                                                            List<TrinityVariantFiring> prefix) {
        Map<AEKey, BigInteger> prefixNet = TrinityDeterministicFiringMath.netChange(
                TrinityDeterministicFiringMath.aggregate(prefix));
        LinkedHashMap<AEKey, BigInteger> primitiveConsumption = new LinkedHashMap<>();
        primitiveFirings.forEach((variant, count) -> variant.inputs().forEach(
                (key, amount) -> primitiveConsumption.merge(key, amount.multiply(count), BigInteger::add)));
        LinkedHashSet<AEKey> keys = new LinkedHashSet<>(component.keys());
        primitiveFirings.keySet().forEach(variant -> keys.addAll(variant.inputs().keySet()));
        keys.addAll(cycleStart.keySet());
        LinkedHashMap<AEKey, BigInteger> maximum = new LinkedHashMap<>();
        for (AEKey key : keys) {
            BigInteger value;
            if (producibleInputs.contains(key)) {
                value = cycleStart.getOrDefault(key, TrinityDeterministicFiringMath.ZERO)
                        .add(primitiveConsumption.getOrDefault(key, TrinityDeterministicFiringMath.ZERO));
            } else {
                value = available.getOrDefault(key, TrinityDeterministicFiringMath.ZERO)
                        .add(prefixNet.getOrDefault(key, TrinityDeterministicFiringMath.ZERO));
            }
            value = value.max(cycleStart.getOrDefault(key, TrinityDeterministicFiringMath.ZERO));
            if (value.signum() < 0) {
                throw new IllegalStateException("A Trinity prefix exceeds a finite cycle-start balance");
            }
            maximum.put(key, value);
        }
        return Collections.unmodifiableMap(maximum);
    }

    private static void mergeRequiredCycleStart(
                                                Map<AEKey, BigInteger> initialInputs,
                                                Map<AEKey, BigInteger> currentCycleStart,
                                                Map<AEKey, BigInteger> requiredCycleStart) {
        requiredCycleStart.forEach((key, required) -> {
            BigInteger deficit = required.subtract(
                    currentCycleStart.getOrDefault(key, TrinityDeterministicFiringMath.ZERO));
            if (deficit.signum() > 0) {
                initialInputs.merge(key, deficit, BigInteger::add);
            }
        });
    }

    private static Map<AEKey, BigInteger> internalAmounts(
                                                          Map<AEKey, BigInteger> amounts,
                                                          List<AEKey> internalKeys) {
        Set<AEKey> internal = Set.copyOf(internalKeys);
        LinkedHashMap<AEKey, BigInteger> selected = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> {
            if (internal.contains(key) && amount.signum() > 0) {
                selected.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(selected);
    }

    private static Map<AEKey, BigInteger> requiredAtStart(TrinityVariantFiring firing) {
        LinkedHashMap<AEKey, BigInteger> required = new LinkedHashMap<>();
        firing.variant().inputs().forEach((key, input) -> {
            BigInteger net = firing.variant().netChange()
                    .getOrDefault(key, TrinityDeterministicFiringMath.ZERO);
            BigInteger amount = net.signum() < 0 ?
                    input.add(net.negate().multiply(firing.count().subtract(BigInteger.ONE))) :
                    input;
            required.put(key, amount);
        });
        return Collections.unmodifiableMap(required);
    }

    private static void apply(TrinityVariantFiring firing, Map<AEKey, BigInteger> balances) {
        firing.variant().netChange().forEach((key, amount) -> {
            BigInteger updated = balances.getOrDefault(key, TrinityDeterministicFiringMath.ZERO)
                    .add(amount.multiply(firing.count()));
            if (updated.signum() < 0) {
                throw new IllegalStateException("A deterministic Trinity batch produced a negative balance");
            }
            if (updated.signum() == 0) {
                balances.remove(key);
            } else {
                balances.put(key, updated);
            }
        });
    }

    private static boolean lacksInputs(
                                       Map<AEKey, BigInteger> balances,
                                       Map<AEKey, BigInteger> required) {
        return required.entrySet().stream().anyMatch(entry -> balances
                .getOrDefault(entry.getKey(), TrinityDeterministicFiringMath.ZERO)
                .compareTo(entry.getValue()) < 0);
    }

    private static void appendBatch(
                                    List<TrinityVariantFiring> batches,
                                    TrinityVariantFiring added) {
        if (!batches.isEmpty() && batches.getLast().variant().equals(added.variant())) {
            TrinityVariantFiring previous = batches.getLast();
            batches.set(
                    batches.size() - 1,
                    new TrinityVariantFiring(previous.variant(), previous.count().add(added.count())));
            return;
        }
        batches.add(added);
    }

    private static Map<AEKey, BigInteger> positiveBalances(Map<AEKey, BigInteger> balances) {
        LinkedHashMap<AEKey, BigInteger> positive = new LinkedHashMap<>();
        balances.forEach((key, amount) -> {
            if (amount.signum() > 0) {
                positive.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(positive);
    }

    private record NormalizedCycle(
                                   List<TrinityVariantFiring> order,
                                   Map<AEKey, BigInteger> initialBalances,
                                   int statesVisited) {}

    private record CycleDecomposition(
                                      BigInteger repetitions,
                                      List<TrinityVariantFiring> prefixOrder,
                                      List<TrinityVariantFiring> suffixOrder) {}
}
