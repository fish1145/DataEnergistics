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
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.macro.TrinityCycleMacro;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityFiringVector;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityLexicographicObjective;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityDeterministicRepeatScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityMinimumSeedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityMinimumSeedScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reconstructs an executable compressed schedule and re-proves both input objectives from exact balances.
 */
final class TrinityDeterministicProofAssemblerImpl implements TrinityDeterministicProofAssembler {

    private final TrinityMinimumSeedScheduler seedScheduler;
    private final TrinityDeterministicRepeatScheduler repeatScheduler;

    TrinityDeterministicProofAssemblerImpl(
                                           TrinityMinimumSeedScheduler seedScheduler,
                                           TrinityDeterministicRepeatScheduler repeatScheduler) {
        if (seedScheduler == null || repeatScheduler == null) {
            throw new IllegalArgumentException("A deterministic proof assembler requires exact schedulers");
        }
        this.seedScheduler = seedScheduler;
        this.repeatScheduler = repeatScheduler;
    }

    @Override
    public TrinityAlgorithmResult<TrinityDeterministicCandidate> assemble(
                                                                          TrinityStronglyConnectedComponent component,
                                                                          TrinityCycleDemand demand,
                                                                          Map<AEKey, BigInteger> available,
                                                                          Set<AEKey> producibleInputs,
                                                                          TrinityDeterministicFiringSolution firingSolution,
                                                                          int maxStates,
                                                                          TrinityPlanningControl control) {
        if (component == null || demand == null || available == null || producibleInputs == null ||
                firingSolution == null || maxStates <= 0 || control == null) {
            throw new IllegalArgumentException("A deterministic proof assembly request is incomplete");
        }
        TrinityDeterministicBasis basis = firingSolution.basis();
        Map<TrinityPatternVariant, BigInteger> firings = firingSolution.firings();
        Map<AEKey, BigInteger> totalNet = firingSolution.totalNet();
        CycleDecomposition decomposition = decompose(
                basis.primitiveFirings(),
                firings,
                basis.reservoir(),
                basis.residualTopology().executionOrder());
        LinkedHashMap<AEKey, BigInteger> conservationInputs = conservationInputs(
                component,
                demand,
                totalNet);
        LinkedHashMap<AEKey, BigInteger> initialInputs = new LinkedHashMap<>(conservationInputs);
        applyRequiredPrefix(decomposition.prefixOrder(), initialInputs);
        if (exceedsAvailable(initialInputs, available, producibleInputs)) {
            return TrinityDeterministicDiagnostics.unsupported();
        }
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
        mergeRequiredCycleStart(initialInputs, cycleStart, normalized.value().initialBalances());
        if (exceedsAvailable(initialInputs, available, producibleInputs)) {
            return TrinityDeterministicDiagnostics.unsupported();
        }

        TrinityAlgorithmResult<TrinityCompressedSchedule> scheduled = schedule(
                decomposition.prefixOrder(),
                normalized.value().order(),
                decomposition.repetitions(),
                decomposition.suffixOrder(),
                initialInputs,
                Math.subtractExact(maxStates, normalized.value().statesVisited()),
                control);
        if (!scheduled.successful()) {
            return TrinityAlgorithmResult.failure(scheduled.diagnostic());
        }
        int totalStates = Math.addExact(
                normalized.value().statesVisited(),
                Math.addExact(firingSolution.balancePasses(), scheduled.value().statesVisited()));
        if (totalStates > maxStates) {
            return TrinityDeterministicDiagnostics.searchLimit(maxStates, totalStates);
        }
        Map<AEKey, BigInteger> scheduleSeed = minimumSeed(
                scheduled.value().batches(),
                component.keys());
        Map<AEKey, BigInteger> minimumSeed = internalAmounts(initialInputs, component.keys());
        BigInteger actualSeed = TrinityDeterministicFiringMath.sum(minimumSeed);
        Optional<TrinityCycleMacro> macro = deterministicMacro(
                component,
                decomposition,
                normalized.value());
        BigInteger provenSeedLowerBound = minimumFirstInternalInput(
                component.cycleVariants(),
                component.keys());
        Map<AEKey, BigInteger> conservationSeed = internalAmounts(conservationInputs, component.keys());
        if (firingSolution.leastFiringsProven() && macro.isPresent()) {
            int remainingStates = Math.subtractExact(maxStates, totalStates);
            if (remainingStates <= 0) {
                return TrinityDeterministicDiagnostics.searchLimit(maxStates, totalStates);
            }
            TrinityAlgorithmResult<TrinityMinimumSeedSchedule> startupProof = proveStartupSeed(
                    component,
                    basis,
                    decomposition,
                    available,
                    producibleInputs,
                    conservationInputs,
                    actualSeed,
                    sumExternal(initialInputs, component.keys()),
                    remainingStates,
                    control);
            if (!startupProof.successful()) {
                return TrinityAlgorithmResult.failure(startupProof.diagnostic());
            }
            provenSeedLowerBound = TrinityDeterministicFiringMath.sum(
                    startupProof.value().minimumSeed());
            totalStates = Math.addExact(
                    totalStates,
                    startupProof.value().schedule().statesVisited());
        } else if (firingSolution.leastFiringsProven() &&
                zeroExternalFiringsCannotReduce(component, conservationSeed)) {
                    provenSeedLowerBound = provenSeedLowerBound.max(
                            TrinityDeterministicFiringMath.sum(conservationSeed));
                }
        if (TrinityDeterministicFiringMath.sum(scheduleSeed).compareTo(actualSeed) > 0) {
            return TrinityDeterministicDiagnostics.unsupported();
        }
        BigInteger externalTotal = sumExternal(initialInputs, component.keys());
        if (firingSolution.leastFiringsProven()) {
            if (!actualSeed.equals(provenSeedLowerBound) ||
                    !externalTotal.equals(minimumExternalReserve(component, demand, totalNet))) {
                return TrinityDeterministicDiagnostics.unsupported();
            }
        } else {
            var global = firingSolution.globalOptimization().orElseThrow(() -> new IllegalStateException(
                    "A non-minimal Trinity firing vector requires a global objective proof"));
            if (!actualSeed.equals(global.minimumSeedLowerBound()) ||
                    !externalTotal.equals(global.minimumExternalInput())) {
                return TrinityDeterministicDiagnostics.unsupported();
            }
        }
        TrinityCompressedSchedule completeSchedule = new TrinityCompressedSchedule(
                scheduled.value().batches(),
                scheduled.value().finalBalances(),
                totalStates);
        Map<AEKey, BigInteger> executionReserve = firingSolution.leastFiringsProven() ?
                includeProducibleReserve(minimumSeed, initialInputs, component.keys(), producibleInputs) :
                minimumSeed;
        TrinityDeterministicComponentPlan plan = new TrinityDeterministicComponentPlan(
                firings,
                executionReserve,
                Collections.unmodifiableMap(new LinkedHashMap<>(initialInputs)),
                totalNet,
                completeSchedule,
                macro.isPresent() ? decomposition.prefixOrder() : List.of(),
                macro,
                macro.isPresent() ? decomposition.suffixOrder() : List.of());
        return TrinityAlgorithmResult.success(new TrinityDeterministicCandidate(
                plan,
                new TrinityLexicographicObjective(
                        externalTotal,
                        actualSeed,
                        TrinityDeterministicFiringMath.sum(firings),
                        TrinityFiringVector.from(component.cycleVariants(), firings))));
    }

    /**
     * Proves the real startup seed on a request-independent kernel instead of scheduling every repeated unit.
     * The residual DAG is included once, together with only the primitive units needed to cover its reservoir
     * deficit. Every omitted primitive unit has non-negative internal net and can therefore be appended without
     * changing the minimum initial marking.
     */
    private TrinityAlgorithmResult<TrinityMinimumSeedSchedule> proveStartupSeed(
                                                                                TrinityStronglyConnectedComponent component,
                                                                                TrinityDeterministicBasis basis,
                                                                                CycleDecomposition decomposition,
                                                                                Map<AEKey, BigInteger> available,
                                                                                Set<AEKey> producibleInputs,
                                                                                Map<AEKey, BigInteger> conservationInputs,
                                                                                BigInteger incumbentSeed,
                                                                                BigInteger externalTotal,
                                                                                int maxStates,
                                                                                TrinityPlanningControl control) {
        Map<TrinityPatternVariant, BigInteger> residualFirings = TrinityDeterministicFiringMath.aggregate(
                combinedOrder(decomposition.prefixOrder(), decomposition.suffixOrder()));
        Map<AEKey, BigInteger> residualNet = TrinityDeterministicFiringMath.netChange(residualFirings);
        BigInteger reservoirEffect = basis.primitiveNet().getOrDefault(
                basis.reservoir(),
                TrinityDeterministicFiringMath.ZERO);
        BigInteger reservoirDeficit = residualNet.getOrDefault(
                basis.reservoir(),
                TrinityDeterministicFiringMath.ZERO)
                .add(conservationInputs.getOrDefault(
                        basis.reservoir(),
                        TrinityDeterministicFiringMath.ZERO))
                .negate()
                .max(TrinityDeterministicFiringMath.ZERO);
        BigInteger kernelRepetitions = reservoirDeficit.signum() == 0 ?
                BigInteger.ONE :
                TrinityDeterministicFiringMath.ceilDivide(reservoirDeficit, reservoirEffect);
        kernelRepetitions = kernelRepetitions
                .max(BigInteger.ONE)
                .min(decomposition.repetitions());
        Map<TrinityPatternVariant, BigInteger> kernelFirings = TrinityDeterministicFiringMath.aggregateRepeated(
                basis.primitiveFirings(),
                kernelRepetitions,
                residualFirings);
        Set<AEKey> externalKeys = externalInputKeys(component, conservationInputs.keySet());
        Set<AEKey> internalKeys = Set.copyOf(component.keys());
        Map<AEKey, BigInteger> maximumInputs = proofMaximumInputs(
                externalKeys,
                internalKeys,
                available,
                producibleInputs,
                conservationInputs,
                incumbentSeed,
                externalTotal);
        return this.seedScheduler.findWithinExternalTotal(
                kernelFirings,
                externalKeys,
                internalKeys,
                conservationInputs,
                maximumInputs,
                externalTotal,
                maxStates,
                control);
    }

    private static List<TrinityVariantFiring> combinedOrder(
                                                            List<TrinityVariantFiring> first,
                                                            List<TrinityVariantFiring> second) {
        ArrayList<TrinityVariantFiring> combined = new ArrayList<>(first.size() + second.size());
        combined.addAll(first);
        combined.addAll(second);
        return List.copyOf(combined);
    }

    private static Set<AEKey> externalInputKeys(
                                                TrinityStronglyConnectedComponent component,
                                                Set<AEKey> requiredInputs) {
        Set<AEKey> internalKeys = Set.copyOf(component.keys());
        LinkedHashSet<AEKey> externalKeys = new LinkedHashSet<>();
        component.cycleVariants().forEach(variant -> variant.inputs().keySet().stream()
                .filter(key -> !internalKeys.contains(key))
                .forEach(externalKeys::add));
        requiredInputs.stream()
                .filter(key -> !internalKeys.contains(key))
                .forEach(externalKeys::add);
        return Collections.unmodifiableSet(externalKeys);
    }

    private static Map<AEKey, BigInteger> proofMaximumInputs(
                                                             Set<AEKey> externalKeys,
                                                             Set<AEKey> internalKeys,
                                                             Map<AEKey, BigInteger> available,
                                                             Set<AEKey> producibleInputs,
                                                             Map<AEKey, BigInteger> minimumInputs,
                                                             BigInteger incumbentSeed,
                                                             BigInteger externalTotal) {
        LinkedHashMap<AEKey, BigInteger> maximum = new LinkedHashMap<>();
        for (AEKey key : externalKeys) {
            maximum.put(key, producibleInputs.contains(key) ?
                    externalTotal :
                    available.getOrDefault(key, TrinityDeterministicFiringMath.ZERO));
        }
        for (AEKey key : internalKeys) {
            BigInteger upper = producibleInputs.contains(key) ?
                    incumbentSeed :
                    available.getOrDefault(key, TrinityDeterministicFiringMath.ZERO).min(incumbentSeed);
            maximum.put(key, upper);
        }
        minimumInputs.forEach((key, amount) -> maximum.merge(key, amount, BigInteger::max));
        return Collections.unmodifiableMap(maximum);
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
        ArrayList<TrinityVariantFiring> batches = new ArrayList<>();
        LinkedHashMap<AEKey, BigInteger> balances = new LinkedHashMap<>(initialInputs);
        int states = 1;
        for (TrinityVariantFiring firing : prefixOrder) {
            TrinityAlgorithmResult<Integer> executed = executeBatch(
                    firing,
                    balances,
                    batches,
                    states,
                    maxStates,
                    control);
            if (!executed.successful()) {
                return TrinityAlgorithmResult.failure(executed.diagnostic());
            }
            states = executed.value();
        }
        if (repetitions.signum() > 0) {
            TrinityAlgorithmResult<TrinityCompressedSchedule> repeated = this.repeatScheduler.schedule(
                    baseOrder,
                    repetitions,
                    positiveBalances(balances),
                    Math.subtractExact(maxStates, states),
                    control);
            if (!repeated.successful()) {
                return repeated;
            }
            batches.addAll(repeated.value().batches());
            balances.clear();
            balances.putAll(repeated.value().finalBalances());
            states = Math.addExact(states, repeated.value().statesVisited());
        }
        for (TrinityVariantFiring firing : suffixOrder) {
            TrinityAlgorithmResult<Integer> executed = executeBatch(
                    firing,
                    balances,
                    batches,
                    states,
                    maxStates,
                    control);
            if (!executed.successful()) {
                return TrinityAlgorithmResult.failure(executed.diagnostic());
            }
            states = executed.value();
        }
        return TrinityAlgorithmResult.success(new TrinityCompressedSchedule(
                List.copyOf(batches),
                positiveBalances(balances),
                states));
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

    private static boolean exceedsAvailable(
                                            Map<AEKey, BigInteger> inputs,
                                            Map<AEKey, BigInteger> available,
                                            Set<AEKey> producibleInputs) {
        return inputs.entrySet().stream().anyMatch(entry -> !producibleInputs.contains(entry.getKey()) &&
                available.getOrDefault(entry.getKey(), TrinityDeterministicFiringMath.ZERO)
                        .compareTo(entry.getValue()) < 0);
    }

    private static Map<AEKey, BigInteger> minimumSeed(
                                                      List<TrinityVariantFiring> order,
                                                      List<AEKey> internalKeys) {
        LinkedHashMap<AEKey, BigInteger> requiredInputs = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> balances = new LinkedHashMap<>();
        for (TrinityVariantFiring firing : order) {
            requiredAtStart(firing).forEach((key, required) -> {
                BigInteger deficit = required.subtract(
                        balances.getOrDefault(key, TrinityDeterministicFiringMath.ZERO));
                if (deficit.signum() > 0) {
                    requiredInputs.merge(key, deficit, BigInteger::add);
                    balances.merge(key, deficit, BigInteger::add);
                }
            });
            apply(firing, balances);
        }
        return internalAmounts(requiredInputs, internalKeys);
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

    private static Map<AEKey, BigInteger> includeProducibleReserve(
                                                                   Map<AEKey, BigInteger> minimumSeed,
                                                                   Map<AEKey, BigInteger> initialInputs,
                                                                   List<AEKey> internalKeys,
                                                                   Set<AEKey> producibleInputs) {
        Set<AEKey> internal = Set.copyOf(internalKeys);
        LinkedHashMap<AEKey, BigInteger> reserve = new LinkedHashMap<>(minimumSeed);
        initialInputs.forEach((key, amount) -> {
            if (!internal.contains(key) && producibleInputs.contains(key) && amount.signum() > 0) {
                reserve.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(reserve);
    }

    /**
     * Extra firings cannot improve the primary external-input objective when the firing vector is componentwise
     * least and patterns only produce internal keys. At the same external level, only transitions without external
     * inputs remain possible. If none of those transitions has positive net production for a key required by the
     * conservation seed, that exact conservation amount is also a global seed lower bound.
     */
    private static boolean zeroExternalFiringsCannotReduce(
                                                           TrinityStronglyConnectedComponent component,
                                                           Map<AEKey, BigInteger> conservationSeed) {
        if (conservationSeed.isEmpty()) {
            return false;
        }
        Set<AEKey> internalKeys = Set.copyOf(component.keys());
        return component.cycleVariants().stream()
                .filter(variant -> internalKeys.containsAll(variant.inputs().keySet()))
                .noneMatch(variant -> conservationSeed.keySet().stream().anyMatch(key -> variant.netChange()
                        .getOrDefault(key, TrinityDeterministicFiringMath.ZERO)
                        .signum() > 0));
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

    private static BigInteger sumExternal(Map<AEKey, BigInteger> amounts, List<AEKey> internalKeys) {
        Set<AEKey> internal = Set.copyOf(internalKeys);
        return amounts.entrySet().stream()
                .filter(entry -> !internal.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .reduce(TrinityDeterministicFiringMath.ZERO, BigInteger::add);
    }

    private static Optional<TrinityCycleMacro> deterministicMacro(
                                                                  TrinityStronglyConnectedComponent component,
                                                                  CycleDecomposition decomposition,
                                                                  NormalizedCycle normalized) {
        if (decomposition.repetitions().signum() <= 0) {
            return Optional.empty();
        }
        Map<AEKey, BigInteger> unitNet = TrinityDeterministicFiringMath.netChange(
                TrinityDeterministicFiringMath.aggregate(normalized.order()));
        if (component.keys().stream().anyMatch(
                key -> unitNet.getOrDefault(key, TrinityDeterministicFiringMath.ZERO).signum() < 0)) {
            return Optional.empty();
        }
        LinkedHashMap<AEKey, BigInteger> exports = new LinkedHashMap<>();
        unitNet.forEach((key, amount) -> {
            if (amount.signum() > 0) {
                exports.put(key, amount);
            }
        });
        return Optional.of(new TrinityCycleMacro(
                normalized.order(),
                decomposition.repetitions(),
                internalAmounts(normalized.initialBalances(), component.keys()),
                unitNet,
                Collections.unmodifiableMap(exports)));
    }

    private static BigInteger minimumExternalReserve(
                                                     TrinityStronglyConnectedComponent component,
                                                     TrinityCycleDemand demand,
                                                     Map<AEKey, BigInteger> netChange) {
        Set<AEKey> internalKeys = Set.copyOf(component.keys());
        LinkedHashSet<AEKey> externalKeys = new LinkedHashSet<>();
        component.cycleVariants().forEach(variant -> variant.inputs().keySet().stream()
                .filter(key -> !internalKeys.contains(key))
                .forEach(externalKeys::add));
        demand.finalBalanceLowerBounds().keySet().stream()
                .filter(key -> !internalKeys.contains(key))
                .forEach(externalKeys::add);
        BigInteger conservationReserve = externalKeys.stream()
                .map(key -> demand.finalBalanceLowerBounds()
                        .getOrDefault(key, TrinityDeterministicFiringMath.ZERO)
                        .subtract(netChange.getOrDefault(key, TrinityDeterministicFiringMath.ZERO))
                        .max(TrinityDeterministicFiringMath.ZERO))
                .reduce(TrinityDeterministicFiringMath.ZERO, BigInteger::add);
        return conservationReserve.max(minimumFirstExternalInput(
                component.cycleVariants(),
                internalKeys));
    }

    private static BigInteger minimumFirstInternalInput(
                                                        List<TrinityPatternVariant> variants,
                                                        List<AEKey> internalKeys) {
        Set<AEKey> internal = Set.copyOf(internalKeys);
        return variants.stream()
                .map(variant -> variant.inputs().entrySet().stream()
                        .filter(entry -> internal.contains(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .reduce(TrinityDeterministicFiringMath.ZERO, BigInteger::add))
                .filter(amount -> amount.signum() > 0)
                .min(BigInteger::compareTo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "A deterministic Trinity component must consume an internal key"));
    }

    private static BigInteger minimumFirstExternalInput(
                                                        List<TrinityPatternVariant> variants,
                                                        Set<AEKey> internalKeys) {
        return variants.stream()
                .map(variant -> variant.inputs().entrySet().stream()
                        .filter(entry -> !internalKeys.contains(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .reduce(TrinityDeterministicFiringMath.ZERO, BigInteger::add))
                .min(BigInteger::compareTo)
                .orElse(TrinityDeterministicFiringMath.ZERO);
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
