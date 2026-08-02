package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.opportunity.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityFiringVector;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityLexicographicObjective;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityShiftedFiringOptimizer;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityDeterministicRepeatScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityMinimumSeedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityMinimumSeedScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Unique-producer implementation that separates one productive cycle basis from an acyclic residual graph.
 */
final class TrinityDeterministicComponentPlannerImpl implements TrinityDeterministicComponentPlanner {

    private static final BigInteger ZERO = BigInteger.ZERO;

    private final TrinityDeterministicCycleSequence cycleSequence;
    private final TrinityShiftedFiringOptimizer firingOptimizer;
    private final TrinityMinimumSeedScheduler seedScheduler;
    private final TrinityDeterministicRepeatScheduler repeatScheduler;

    TrinityDeterministicComponentPlannerImpl(TrinityDeterministicCycleSequence cycleSequence,
                                             TrinityShiftedFiringOptimizer firingOptimizer,
                                             TrinityMinimumSeedScheduler seedScheduler,
                                             TrinityDeterministicRepeatScheduler repeatScheduler) {
        this.cycleSequence = cycleSequence;
        this.firingOptimizer = firingOptimizer;
        this.seedScheduler = seedScheduler;
        this.repeatScheduler = repeatScheduler;
    }

    @Override
    public TrinityPlanningAttempt<TrinityDeterministicComponentPlan> plan(
                                                                          TrinityStronglyConnectedComponent component,
                                                                          TrinityCycleDemand demand,
                                                                          Map<AEKey, BigInteger> available,
                                                                          Set<AEKey> producibleInputs,
                                                                          int maxStates,
                                                                          TrinityPlanningControl control) {
        if (component == null || !component.cyclic() || demand == null || available == null ||
                producibleInputs == null || maxStates <= 0 || control == null) {
            throw new IllegalArgumentException("A deterministic Trinity component request is incomplete");
        }
        Map<AEKey, BigInteger> inventory = copyAvailable(available);
        Set<AEKey> producible = Set.copyOf(producibleInputs);
        ArrayList<Candidate> candidates = new ArrayList<>();

        for (AEKey reservoir : component.keys()) {
            StopState state = stopState(control);
            if (state != StopState.RUNNING) {
                return TrinityPlanningAttempt.terminal(stopped(state).diagnostic());
            }
            Optional<List<TrinityVariantFiring>> primitive = this.cycleSequence.resolve(
                    component,
                    reservoir,
                    inventory);
            if (primitive.isEmpty()) {
                continue;
            }
            if (!isProductiveBasis(component, demand, reservoir, primitive.orElseThrow())) {
                continue;
            }
            Optional<ResidualTopology> topology = ResidualTopology.create(component, reservoir);
            if (topology.isEmpty()) {
                return notApplicable("A productive Trinity basis has an ambiguous residual route");
            }
            TrinityAlgorithmResult<Candidate> attempted = solveCandidate(
                    component,
                    demand,
                    inventory,
                    producible,
                    reservoir,
                    primitive.orElseThrow(),
                    topology.orElseThrow(),
                    maxStates,
                    control);
            if (attempted.successful()) {
                candidates.add(attempted.value());
                continue;
            }
            TrinityPlanningDiagnosticCode code = attempted.diagnostic().code();
            if (code == TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED ||
                    code == TrinityPlanningDiagnosticCode.MIP_TIMEOUT) {
                return TrinityPlanningAttempt.terminal(attempted.diagnostic());
            }
            return TrinityPlanningAttempt.notApplicable(attempted.diagnostic());
        }
        if (candidates.isEmpty()) {
            return notApplicable("No unique productive Trinity component basis was proved");
        }
        TrinityFiringVector firstVector = candidates.getFirst().objective().identity();
        if (candidates.stream().anyMatch(candidate -> !candidate.objective().identity().equals(firstVector))) {
            return notApplicable("Productive Trinity bases disagree on the complete firing identity");
        }
        candidates.sort(Candidate.ORDER);
        return TrinityPlanningAttempt.provedOptimal(candidates.getFirst().plan());
    }

    private TrinityAlgorithmResult<Candidate> solveCandidate(
                                                             TrinityStronglyConnectedComponent component,
                                                             TrinityCycleDemand demand,
                                                             Map<AEKey, BigInteger> available,
                                                             Set<AEKey> producibleInputs,
                                                             AEKey reservoir,
                                                             List<TrinityVariantFiring> primitiveOrder,
                                                             ResidualTopology topology,
                                                             int maxStates,
                                                             TrinityPlanningControl control) {
        LinkedHashMap<TrinityPatternVariant, BigInteger> primitiveFirings = aggregate(primitiveOrder);
        Map<AEKey, BigInteger> primitiveNet = netChange(primitiveFirings);
        BigInteger reservoirEffect = primitiveNet.getOrDefault(reservoir, ZERO);
        if (reservoirEffect.signum() <= 0) {
            return unsupported("The deterministic Trinity basis is not productive");
        }
        for (AEKey key : component.keys()) {
            if (!key.equals(reservoir) && primitiveNet.getOrDefault(key, ZERO).signum() != 0) {
                return unsupported("The deterministic Trinity basis did not isolate one reservoir");
            }
        }
        for (AEKey demanded : demand.requiredNetChangeLowerBounds().keySet()) {
            if (primitiveNet.getOrDefault(demanded, ZERO).signum() < 0) {
                return unsupported("A deterministic Trinity basis consumes another demanded output");
            }
        }

        Map<AEKey, BigInteger> netLowerBounds = netLowerBounds(
                component,
                demand,
                available,
                producibleInputs);
        BigInteger repetitions = initialRepetitions(demand, primitiveNet);
        ResidualResult residual = null;
        int balancePasses = 0;
        int balancePassLimit = Math.addExact(component.cycleVariants().size(), 2);
        while (balancePasses < balancePassLimit) {
            balancePasses = Math.incrementExact(balancePasses);
            TrinityAlgorithmResult<ResidualResult> solvedResidual = topology.solveResidual(
                    demand.requiredNetChangeLowerBounds(),
                    primitiveNet,
                    repetitions);
            if (!solvedResidual.successful()) {
                return TrinityAlgorithmResult.failure(solvedResidual.diagnostic());
            }
            residual = solvedResidual.value();
            if (hasPositiveEffectOnPrimitiveAxis(residual.netChange(), primitiveNet)) {
                return unsupported("Residual Trinity work changes a productive basis axis in both directions");
            }

            Map<AEKey, BigInteger> combinedNet = addSigned(
                    multiplySigned(primitiveNet, repetitions),
                    residual.netChange());
            BigInteger jump = requiredRepetitionJump(combinedNet, netLowerBounds, primitiveNet);
            if (jump.signum() < 0) {
                return unsupported("A deterministic Trinity basis cannot repair a remaining balance deficit");
            }
            if (jump.signum() == 0) {
                break;
            }
            repetitions = repetitions.add(jump);
        }
        if (residual == null || !satisfies(
                addSigned(multiplySigned(primitiveNet, repetitions), residual.netChange()),
                netLowerBounds)) {
            return unsupported("The deterministic Trinity balance did not converge within its graph bound");
        }

        LinkedHashMap<TrinityPatternVariant, BigInteger> baselineFirings = aggregateRepeated(
                primitiveFirings,
                repetitions,
                residual.firings());
        if (baselineFirings.isEmpty()) {
            return unsupported("A deterministic Trinity component produced no work");
        }
        TrinityPlanningAttempt<Map<TrinityPatternVariant, BigInteger>> optimized = this.firingOptimizer.optimize(
                component,
                demand,
                available,
                producibleInputs,
                baselineFirings,
                control);
        if (optimized.kind() != TrinityPlanningAttempt.Kind.PROVED_OPTIMAL) {
            return TrinityAlgorithmResult.failure(optimized.diagnostic());
        }
        Map<TrinityPatternVariant, BigInteger> firings = optimized.value();
        Map<AEKey, BigInteger> totalNet = netChange(firings);
        if (!satisfies(totalNet, netLowerBounds)) {
            return unsupported("A deterministic Trinity component failed an exact net lower bound");
        }

        CycleDecomposition decomposition = decompose(
                primitiveFirings,
                firings,
                reservoir,
                topology.executionOrder);
        LinkedHashMap<AEKey, BigInteger> initialInputs = conservationInputs(
                component,
                demand,
                totalNet);
        applyRequiredPrefix(decomposition.prefixOrder(), initialInputs);
        if (exceedsAvailable(initialInputs, available, producibleInputs)) {
            return unsupported("The deterministic Trinity prefix requires unavailable inputs");
        }
        LinkedHashMap<AEKey, BigInteger> cycleStart = simulate(
                initialInputs,
                decomposition.prefixOrder());
        Map<AEKey, BigInteger> cycleMaximum = cycleStartMaximum(
                component,
                primitiveFirings,
                available,
                producibleInputs,
                cycleStart,
                decomposition.prefixOrder());
        TrinityAlgorithmResult<NormalizedCycle> normalized = normalizePrimitiveCycle(
                component,
                primitiveFirings,
                cycleStart,
                cycleMaximum,
                maxStates,
                control);
        if (!normalized.successful()) {
            return TrinityAlgorithmResult.failure(normalized.diagnostic());
        }
        mergeRequiredCycleStart(initialInputs, cycleStart, normalized.value().initialBalances());
        if (exceedsAvailable(initialInputs, available, producibleInputs)) {
            return unsupported("The normalized Trinity cycle requires unavailable inputs");
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
                Math.addExact(balancePasses, scheduled.value().statesVisited()));
        if (totalStates > maxStates) {
            return searchLimit(maxStates, totalStates);
        }
        TrinityCompressedSchedule completeSchedule = new TrinityCompressedSchedule(
                scheduled.value().batches(),
                scheduled.value().finalBalances(),
                totalStates);
        Map<AEKey, BigInteger> minimumSeed = minimumSeed(
                completeSchedule.batches(),
                component.keys());
        BigInteger provenSeedLowerBound = minimumFirstInternalInput(
                component.cycleVariants(),
                component.keys());
        if (!sum(minimumSeed).equals(provenSeedLowerBound)) {
            return unsupported("The compressed Trinity order did not attain the proven seed lower bound");
        }
        BigInteger externalTotal = sumExternal(initialInputs, component.keys());
        if (!externalTotal.equals(minimumExternalReserve(
                component,
                demand,
                totalNet))) {
            return unsupported("The compressed Trinity order requires an unmodelled external prefix reserve");
        }
        TrinityDeterministicComponentPlan plan = new TrinityDeterministicComponentPlan(
                firings,
                minimumSeed,
                Collections.unmodifiableMap(new LinkedHashMap<>(initialInputs)),
                totalNet,
                completeSchedule);
        return TrinityAlgorithmResult.success(new Candidate(
                plan,
                new TrinityLexicographicObjective(
                        externalTotal,
                        sum(minimumSeed),
                        sum(firings),
                        TrinityFiringVector.from(component.cycleVariants(), firings))));
    }

    private static boolean isProductiveBasis(
                                             TrinityStronglyConnectedComponent component,
                                             TrinityCycleDemand demand,
                                             AEKey reservoir,
                                             List<TrinityVariantFiring> primitiveOrder) {
        Map<AEKey, BigInteger> primitiveNet = netChange(aggregate(primitiveOrder));
        if (primitiveNet.getOrDefault(reservoir, ZERO).signum() <= 0) {
            return false;
        }
        if (component.keys().stream()
                .filter(key -> !key.equals(reservoir))
                .anyMatch(key -> primitiveNet.getOrDefault(key, ZERO).signum() != 0)) {
            return false;
        }
        return demand.requiredNetChangeLowerBounds().keySet().stream()
                .noneMatch(key -> primitiveNet.getOrDefault(key, ZERO).signum() < 0);
    }

    private static Map<AEKey, BigInteger> minimumSeed(
                                                      List<TrinityVariantFiring> order,
                                                      List<AEKey> internalKeys) {
        LinkedHashMap<AEKey, BigInteger> requiredInputs = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> balances = new LinkedHashMap<>();
        for (TrinityVariantFiring firing : order) {
            requiredAtStart(firing).forEach((key, required) -> {
                BigInteger deficit = required.subtract(balances.getOrDefault(key, ZERO));
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
                minimumBalances,
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

    private TrinityAlgorithmResult<TrinityCompressedSchedule> schedule(
                                                                       List<TrinityVariantFiring> prefixOrder,
                                                                       List<TrinityVariantFiring> baseOrder,
                                                                       BigInteger repetitions,
                                                                       List<TrinityVariantFiring> suffixOrder,
                                                                       Map<AEKey, BigInteger> initialInputs,
                                                                       int maxStates,
                                                                       TrinityPlanningControl control) {
        if (maxStates <= 0) {
            return searchLimit(0, 0);
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
        StopState state = stopState(control);
        if (state != StopState.RUNNING) {
            return stopped(state);
        }
        if (states >= maxStates) {
            return searchLimit(maxStates, states);
        }
        if (!hasInputs(balances, requiredAtStart(firing))) {
            return failure(
                    TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                    "A deterministic Trinity residual batch is not executable",
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
            BigInteger available = firings.getOrDefault(primitive.getKey(), ZERO);
            BigInteger supported = available.divide(primitive.getValue());
            repetitions = repetitions == null ? supported : repetitions.min(supported);
        }
        if (repetitions == null) {
            throw new IllegalStateException("A deterministic Trinity basis cannot be empty");
        }
        LinkedHashMap<TrinityPatternVariant, BigInteger> residual = new LinkedHashMap<>();
        for (TrinityPatternVariant variant : topologicalOrder) {
            BigInteger count = firings.getOrDefault(variant, ZERO)
                    .subtract(primitiveFirings.getOrDefault(variant, ZERO).multiply(repetitions));
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
            if (variant.netChange().getOrDefault(reservoir, ZERO).signum() > 0) {
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
                    .getOrDefault(key, ZERO)
                    .subtract(netChange.getOrDefault(key, ZERO))
                    .max(ZERO);
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
                BigInteger deficit = required.subtract(balances.getOrDefault(key, ZERO));
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
            if (!hasInputs(balances, requiredAtStart(firing))) {
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
        Map<AEKey, BigInteger> prefixNet = netChange(aggregate(prefix));
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
                value = cycleStart.getOrDefault(key, ZERO)
                        .add(primitiveConsumption.getOrDefault(key, ZERO));
            } else {
                value = available.getOrDefault(key, ZERO)
                        .add(prefixNet.getOrDefault(key, ZERO));
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
            BigInteger deficit = required.subtract(currentCycleStart.getOrDefault(key, ZERO));
            if (deficit.signum() > 0) {
                initialInputs.merge(key, deficit, BigInteger::add);
            }
        });
    }

    private static Map<AEKey, BigInteger> netLowerBounds(
                                                         TrinityStronglyConnectedComponent component,
                                                         TrinityCycleDemand demand,
                                                         Map<AEKey, BigInteger> available,
                                                         Set<AEKey> producibleInputs) {
        LinkedHashMap<AEKey, BigInteger> lower = new LinkedHashMap<>(
                demand.requiredNetChangeLowerBounds());
        LinkedHashSet<AEKey> touched = new LinkedHashSet<>(component.keys());
        component.cycleVariants().forEach(variant -> touched.addAll(variant.netChange().keySet()));
        touched.addAll(demand.finalBalanceLowerBounds().keySet());
        for (AEKey key : touched) {
            if (producibleInputs.contains(key)) {
                continue;
            }
            BigInteger finiteLower = demand.finalBalanceLowerBounds()
                    .getOrDefault(key, ZERO)
                    .subtract(available.getOrDefault(key, ZERO));
            lower.merge(key, finiteLower, BigInteger::max);
        }
        return Collections.unmodifiableMap(lower);
    }

    private static BigInteger initialRepetitions(
                                                 TrinityCycleDemand demand,
                                                 Map<AEKey, BigInteger> primitiveNet) {
        BigInteger repetitions = ZERO;
        for (Map.Entry<AEKey, BigInteger> required : demand.requiredNetChangeLowerBounds().entrySet()) {
            BigInteger effect = primitiveNet.getOrDefault(required.getKey(), ZERO);
            if (effect.signum() > 0) {
                repetitions = repetitions.max(ceilDivide(required.getValue(), effect));
            }
        }
        return repetitions;
    }

    private static BigInteger requiredRepetitionJump(
                                                     Map<AEKey, BigInteger> combinedNet,
                                                     Map<AEKey, BigInteger> lowerBounds,
                                                     Map<AEKey, BigInteger> primitiveNet) {
        BigInteger jump = ZERO;
        for (Map.Entry<AEKey, BigInteger> bound : lowerBounds.entrySet()) {
            BigInteger deficit = bound.getValue().subtract(combinedNet.getOrDefault(bound.getKey(), ZERO));
            if (deficit.signum() <= 0) {
                continue;
            }
            BigInteger effect = primitiveNet.getOrDefault(bound.getKey(), ZERO);
            if (effect.signum() <= 0) {
                return BigInteger.valueOf(-1L);
            }
            jump = jump.max(ceilDivide(deficit, effect));
        }
        return jump;
    }

    private static boolean satisfies(
                                     Map<AEKey, BigInteger> net,
                                     Map<AEKey, BigInteger> lowerBounds) {
        return lowerBounds.entrySet().stream().allMatch(entry -> net
                .getOrDefault(entry.getKey(), ZERO)
                .compareTo(entry.getValue()) >= 0);
    }

    private static boolean hasPositiveEffectOnPrimitiveAxis(
                                                            Map<AEKey, BigInteger> residualNet,
                                                            Map<AEKey, BigInteger> primitiveNet) {
        return primitiveNet.entrySet().stream()
                .filter(entry -> entry.getValue().signum() > 0)
                .anyMatch(entry -> residualNet.getOrDefault(entry.getKey(), ZERO).signum() > 0);
    }

    private static boolean exceedsAvailable(
                                            Map<AEKey, BigInteger> inputs,
                                            Map<AEKey, BigInteger> available,
                                            Set<AEKey> producibleInputs) {
        return inputs.entrySet().stream().anyMatch(entry -> !producibleInputs.contains(entry.getKey()) &&
                available.getOrDefault(entry.getKey(), ZERO).compareTo(entry.getValue()) < 0);
    }

    private static LinkedHashMap<TrinityPatternVariant, BigInteger> aggregate(
                                                                              List<TrinityVariantFiring> order) {
        LinkedHashMap<TrinityPatternVariant, BigInteger> aggregate = new LinkedHashMap<>();
        order.forEach(firing -> aggregate.merge(firing.variant(), firing.count(), BigInteger::add));
        return aggregate;
    }

    private static LinkedHashMap<TrinityPatternVariant, BigInteger> aggregateRepeated(
                                                                                      Map<TrinityPatternVariant, BigInteger> primitive,
                                                                                      BigInteger repetitions,
                                                                                      Map<TrinityPatternVariant, BigInteger> residual) {
        LinkedHashMap<TrinityPatternVariant, BigInteger> aggregate = new LinkedHashMap<>();
        primitive.forEach((variant, count) -> {
            BigInteger repeated = count.multiply(repetitions);
            if (repeated.signum() > 0) {
                aggregate.put(variant, repeated);
            }
        });
        residual.forEach((variant, count) -> aggregate.merge(variant, count, BigInteger::add));
        return aggregate;
    }

    private static Map<AEKey, BigInteger> netChange(
                                                    Map<TrinityPatternVariant, BigInteger> firings) {
        LinkedHashMap<AEKey, BigInteger> net = new LinkedHashMap<>();
        firings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> net.merge(key, amount.multiply(count), BigInteger::add)));
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return Collections.unmodifiableMap(net);
    }

    private static Map<AEKey, BigInteger> multiplySigned(
                                                         Map<AEKey, BigInteger> amounts,
                                                         BigInteger multiplier) {
        LinkedHashMap<AEKey, BigInteger> multiplied = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> {
            BigInteger result = amount.multiply(multiplier);
            if (result.signum() != 0) {
                multiplied.put(key, result);
            }
        });
        return Collections.unmodifiableMap(multiplied);
    }

    private static Map<AEKey, BigInteger> addSigned(
                                                    Map<AEKey, BigInteger> first,
                                                    Map<AEKey, BigInteger> second) {
        LinkedHashMap<AEKey, BigInteger> result = new LinkedHashMap<>(first);
        second.forEach((key, amount) -> result.merge(key, amount, BigInteger::add));
        result.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return Collections.unmodifiableMap(result);
    }

    private static Map<AEKey, BigInteger> requiredAtStart(TrinityVariantFiring firing) {
        LinkedHashMap<AEKey, BigInteger> required = new LinkedHashMap<>();
        firing.variant().inputs().forEach((key, input) -> {
            BigInteger net = firing.variant().netChange().getOrDefault(key, ZERO);
            BigInteger amount = net.signum() < 0 ?
                    input.add(net.negate().multiply(firing.count().subtract(BigInteger.ONE))) :
                    input;
            required.put(key, amount);
        });
        return Collections.unmodifiableMap(required);
    }

    private static void apply(TrinityVariantFiring firing, Map<AEKey, BigInteger> balances) {
        firing.variant().netChange().forEach((key, amount) -> {
            BigInteger updated = balances.getOrDefault(key, ZERO).add(amount.multiply(firing.count()));
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

    private static boolean hasInputs(
                                     Map<AEKey, BigInteger> balances,
                                     Map<AEKey, BigInteger> required) {
        return required.entrySet().stream().allMatch(entry -> balances
                .getOrDefault(entry.getKey(), ZERO)
                .compareTo(entry.getValue()) >= 0);
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

    private static Map<AEKey, BigInteger> copyAvailable(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("Trinity deterministic-component inventory cannot be negative");
            }
            if (amount.signum() > 0) {
                copied.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(copied);
    }

    private static BigInteger sum(Map<?, BigInteger> amounts) {
        return amounts.values().stream().reduce(ZERO, BigInteger::add);
    }

    private static BigInteger sumExternal(Map<AEKey, BigInteger> amounts, List<AEKey> internalKeys) {
        Set<AEKey> internal = Set.copyOf(internalKeys);
        return amounts.entrySet().stream()
                .filter(entry -> !internal.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .reduce(ZERO, BigInteger::add);
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
                .map(key -> demand.finalBalanceLowerBounds().getOrDefault(key, ZERO)
                        .subtract(netChange.getOrDefault(key, ZERO))
                        .max(ZERO))
                .reduce(ZERO, BigInteger::add);
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
                        .reduce(ZERO, BigInteger::add))
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
                        .reduce(ZERO, BigInteger::add))
                .min(BigInteger::compareTo)
                .orElse(ZERO);
    }

    private static BigInteger ceilDivide(BigInteger numerator, BigInteger denominator) {
        if (numerator.signum() <= 0 || denominator.signum() <= 0) {
            throw new IllegalArgumentException("A deterministic Trinity ratio requires positive values");
        }
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }

    private static StopState stopState(TrinityPlanningControl control) {
        if (control.cancellationRequested()) {
            return StopState.CANCELLED;
        }
        return control.deadlineExceeded() ? StopState.DEADLINE_EXCEEDED : StopState.RUNNING;
    }

    private static <T> TrinityAlgorithmResult<T> stopped(StopState state) {
        return state == StopState.CANCELLED ?
                failure(
                        TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                        "Trinity deterministic component planning was cancelled",
                        Map.of()) :
                failure(
                        TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                        "Trinity deterministic component planning exhausted its deadline",
                        Map.of("phase", "deterministic_component"));
    }

    private static <T> TrinityAlgorithmResult<T> unsupported(String detail) {
        return failure(
                TrinityPlanningDiagnosticCode.UNSUPPORTED_PATTERN,
                detail,
                Map.of("phase", "deterministic_component"));
    }

    private static <T> TrinityPlanningAttempt<T> notApplicable(String detail) {
        return TrinityPlanningAttempt.notApplicable(unsupported(detail).diagnostic());
    }

    private static <T> TrinityAlgorithmResult<T> searchLimit(int limit, int states) {
        return failure(
                TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                "Trinity deterministic component planning exceeded its state limit",
                Map.of("limit", Integer.toString(limit), "states", Integer.toString(states)));
    }

    private static <T> TrinityAlgorithmResult<T> failure(
                                                         TrinityPlanningDiagnosticCode code,
                                                         String detail,
                                                         Map<String, String> metadata) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                code,
                Component.literal(detail),
                metadata));
    }

    private record NormalizedCycle(
                                   List<TrinityVariantFiring> order,
                                   Map<AEKey, BigInteger> initialBalances,
                                   int statesVisited) {}

    private record CycleDecomposition(
                                      BigInteger repetitions,
                                      List<TrinityVariantFiring> prefixOrder,
                                      List<TrinityVariantFiring> suffixOrder) {}

    private record ResidualResult(
                                  Map<TrinityPatternVariant, BigInteger> firings,
                                  Map<AEKey, BigInteger> netChange,
                                  List<TrinityVariantFiring> executionOrder) {}

    private record Candidate(
                             TrinityDeterministicComponentPlan plan,
                             TrinityLexicographicObjective objective) {

        private static final Comparator<Candidate> ORDER = Comparator.comparing(Candidate::objective);
    }

    private static final class ResidualTopology {

        private final AEKey reservoir;
        private final Map<AEKey, TrinityPatternVariant> producerByKey;
        private final Set<AEKey> ambiguousOutputs;
        private final List<TrinityPatternVariant> executionOrder;

        private ResidualTopology(AEKey reservoir,
                                 Map<AEKey, TrinityPatternVariant> producerByKey,
                                 Set<AEKey> ambiguousOutputs,
                                 List<TrinityPatternVariant> executionOrder) {
            this.reservoir = reservoir;
            this.producerByKey = producerByKey;
            this.ambiguousOutputs = ambiguousOutputs;
            this.executionOrder = executionOrder;
        }

        private static Optional<ResidualTopology> create(
                                                         TrinityStronglyConnectedComponent component,
                                                         AEKey reservoir) {
            List<TrinityPatternVariant> variants = component.cycleVariants().stream().sorted().toList();
            LinkedHashMap<AEKey, TrinityPatternVariant> producerByKey = new LinkedHashMap<>();
            LinkedHashSet<AEKey> ambiguous = new LinkedHashSet<>();
            for (TrinityPatternVariant variant : variants) {
                for (AEKey output : variant.outputs().keySet()) {
                    if (output.equals(reservoir) || ambiguous.contains(output)) {
                        continue;
                    }
                    TrinityPatternVariant existing = producerByKey.putIfAbsent(output, variant);
                    if (existing != null && !existing.equals(variant)) {
                        producerByKey.remove(output);
                        ambiguous.add(output);
                    }
                }
            }

            HashMap<TrinityPatternVariant, Integer> indegrees = new HashMap<>();
            HashMap<TrinityPatternVariant, LinkedHashSet<TrinityPatternVariant>> successors = new HashMap<>();
            variants.forEach(variant -> {
                indegrees.put(variant, 0);
                successors.put(variant, new LinkedHashSet<>());
            });
            for (TrinityPatternVariant consumer : variants) {
                for (AEKey input : consumer.inputs().keySet()) {
                    if (input.equals(reservoir)) {
                        continue;
                    }
                    if (ambiguous.contains(input)) {
                        return Optional.empty();
                    }
                    TrinityPatternVariant producer = producerByKey.get(input);
                    if (producer == null) {
                        continue;
                    }
                    if (producer.equals(consumer)) {
                        return Optional.empty();
                    }
                    if (successors.get(producer).add(consumer)) {
                        indegrees.merge(consumer, 1, Integer::sum);
                    }
                }
            }
            ArrayList<TrinityPatternVariant> ready = new ArrayList<>();
            indegrees.forEach((variant, degree) -> {
                if (degree == 0) {
                    ready.add(variant);
                }
            });
            ready.sort(Comparator.naturalOrder());
            ArrayList<TrinityPatternVariant> order = new ArrayList<>(variants.size());
            while (!ready.isEmpty()) {
                TrinityPatternVariant selected = ready.removeFirst();
                order.add(selected);
                for (TrinityPatternVariant successor : successors.get(selected)) {
                    int degree = indegrees.merge(successor, -1, Integer::sum);
                    if (degree == 0) {
                        ready.add(successor);
                        ready.sort(Comparator.naturalOrder());
                    }
                }
            }
            if (order.size() != variants.size()) {
                return Optional.empty();
            }
            return Optional.of(new ResidualTopology(
                    reservoir,
                    Collections.unmodifiableMap(producerByKey),
                    Collections.unmodifiableSet(ambiguous),
                    List.copyOf(order)));
        }

        private TrinityAlgorithmResult<ResidualResult> solveResidual(
                                                                     Map<AEKey, BigInteger> requiredNet,
                                                                     Map<AEKey, BigInteger> primitiveNet,
                                                                     BigInteger repetitions) {
            LinkedHashMap<AEKey, BigInteger> requirements = new LinkedHashMap<>();
            requiredNet.forEach((key, amount) -> {
                if (!key.equals(this.reservoir)) {
                    BigInteger remaining = amount.subtract(
                            primitiveNet.getOrDefault(key, ZERO).multiply(repetitions));
                    if (remaining.signum() > 0) {
                        requirements.put(key, remaining);
                    }
                }
            });
            for (AEKey key : requirements.keySet()) {
                if (this.ambiguousOutputs.contains(key) || !this.producerByKey.containsKey(key)) {
                    return unsupported("A demanded Trinity residual output has no unique producer");
                }
            }

            LinkedHashMap<TrinityPatternVariant, BigInteger> firings = new LinkedHashMap<>();
            for (int index = this.executionOrder.size() - 1; index >= 0; index--) {
                TrinityPatternVariant variant = this.executionOrder.get(index);
                BigInteger count = ZERO;
                for (Map.Entry<AEKey, BigInteger> output : variant.outputs().entrySet()) {
                    if (!variant.equals(this.producerByKey.get(output.getKey()))) {
                        continue;
                    }
                    BigInteger required = requirements.getOrDefault(output.getKey(), ZERO);
                    if (required.signum() > 0) {
                        count = count.max(ceilDivide(required, output.getValue()));
                    }
                }
                if (count.signum() == 0) {
                    continue;
                }
                firings.put(variant, count);
                BigInteger selectedCount = count;
                variant.inputs().forEach((key, amount) -> {
                    if (!key.equals(this.reservoir) && this.producerByKey.containsKey(key)) {
                        requirements.merge(key, amount.multiply(selectedCount), BigInteger::add);
                    }
                });
            }
            Map<AEKey, BigInteger> net = netChange(firings);
            ArrayList<TrinityVariantFiring> ordered = new ArrayList<>();
            for (TrinityPatternVariant variant : this.executionOrder) {
                BigInteger count = firings.getOrDefault(variant, ZERO);
                if (count.signum() > 0) {
                    ordered.add(new TrinityVariantFiring(variant, count));
                }
            }
            return TrinityAlgorithmResult.success(new ResidualResult(
                    Collections.unmodifiableMap(firings),
                    net,
                    List.copyOf(ordered)));
        }
    }

    private enum StopState {
        RUNNING,
        CANCELLED,
        DEADLINE_EXCEEDED
    }
}
