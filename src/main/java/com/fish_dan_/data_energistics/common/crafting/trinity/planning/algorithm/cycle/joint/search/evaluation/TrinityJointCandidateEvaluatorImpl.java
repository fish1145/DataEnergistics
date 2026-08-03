package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.evaluation;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.TrinityJointCyclePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilitySolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityExactConservationVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityFiringVector;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityLexicographicObjective;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityCompressedScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityMinimumSeedSchedule;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityMinimumSeedScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Evaluates exact firing points without owning branch queues or firing-domain decisions.
 */
final class TrinityJointCandidateEvaluatorImpl implements TrinityJointCandidateEvaluator {

    private static final String MIP_TIMEOUT_KEY = "gui.data_energistics.trinity_planning.mip.timeout";
    private static final String NO_ORDER_KEY = "gui.data_energistics.trinity_planning.mip.no_executable_order";
    private static final String SEARCH_LIMIT_KEY = "gui.data_energistics.trinity_planning.mip.schedule_search_limit";

    private final TrinityExactConservationVerifier conservationVerifier;
    private final TrinityCompressedScheduler compressedScheduler;
    private final TrinityMinimumSeedScheduler seedScheduler;

    TrinityJointCandidateEvaluatorImpl(
                                       TrinityExactConservationVerifier conservationVerifier,
                                       TrinityCompressedScheduler compressedScheduler,
                                       TrinityMinimumSeedScheduler seedScheduler) {
        if (conservationVerifier == null || compressedScheduler == null || seedScheduler == null) {
            throw new IllegalArgumentException("A Trinity joint candidate evaluator requires exact planning components");
        }
        this.conservationVerifier = conservationVerifier;
        this.compressedScheduler = compressedScheduler;
        this.seedScheduler = seedScheduler;
    }

    @Override
    public TrinityAlgorithmResult<TrinityJointCandidateEvaluation> evaluate(
                                                                            List<TrinityPatternVariant> variants,
                                                                            Set<AEKey> internalKeys,
                                                                            TrinityCycleDemand demand,
                                                                            Map<AEKey, BigInteger> available,
                                                                            Set<AEKey> producibleInputs,
                                                                            TrinityCycleFeasibilitySolution solution,
                                                                            int maxScheduleStates,
                                                                            int solverPasses,
                                                                            long solverNanos,
                                                                            TrinityPlanningControl control) {
        if (variants == null || variants.isEmpty() || internalKeys == null || internalKeys.isEmpty() ||
                demand == null || available == null || producibleInputs == null || solution == null ||
                maxScheduleStates <= 0 || solverPasses <= 0 || solverNanos < 0L || control == null) {
            throw new IllegalArgumentException("A Trinity joint candidate evaluation request is incomplete");
        }
        List<TrinityPatternVariant> orderedVariants = variants.stream().sorted().toList();
        Set<AEKey> externalKeys = externalReserveKeys(orderedVariants, internalKeys, demand);
        CandidateAccounting accounting = accountCandidate(solution.firings(), internalKeys, demand)
                .orElseThrow(() -> new IllegalStateException(
                        "An exact Trinity MIP solution failed its demand accounting"));
        if (exceedsAvailable(accounting.externalInputs(), available, producibleInputs) ||
                exceedsAvailable(accounting.requiredModelSeed(), available, producibleInputs)) {
            throw new IllegalStateException("An exact Trinity MIP candidate exceeded its input domain");
        }

        Map<AEKey, BigInteger> maximumInputs = candidateInputBounds(
                solution.firings(),
                externalKeys,
                internalKeys,
                accounting,
                available,
                producibleInputs);
        LinkedHashMap<AEKey, BigInteger> minimumInputs = new LinkedHashMap<>(accounting.externalInputs());
        accounting.requiredModelSeed().forEach(
                (key, amount) -> minimumInputs.merge(key, amount, BigInteger::add));
        TrinityAlgorithmResult<TrinityMinimumSeedSchedule> scheduledResult = findExecutableInputs(
                solution.firings(),
                externalKeys,
                internalKeys,
                minimumInputs,
                maximumInputs,
                solution.seedTotal(),
                maxScheduleStates,
                control);
        if (!scheduledResult.successful()) {
            return TrinityAlgorithmResult.failure(scheduledResult.diagnostic());
        }
        TrinityMinimumSeedSchedule scheduled = scheduledResult.value();
        requireExternalInputs(scheduled.externalInputs(), accounting.externalInputs());

        LinkedHashMap<AEKey, BigInteger> initialInputs = new LinkedHashMap<>(scheduled.externalInputs());
        scheduled.minimumSeed().forEach((key, amount) -> initialInputs.merge(key, amount, BigInteger::add));
        TrinityAlgorithmResult<Map<AEKey, BigInteger>> exact = this.conservationVerifier.verify(
                orderedVariants,
                solution.firings(),
                initialInputs,
                finiteInputUpperBounds(orderedVariants, internalKeys, demand, available, producibleInputs),
                demand.finalBalanceLowerBounds(),
                demand.requiredNetChangeLowerBounds());
        if (!exact.successful()) {
            return TrinityAlgorithmResult.failure(exact.diagnostic());
        }

        Map<AEKey, BigInteger> finalBalances = addSigned(initialInputs, accounting.netChange());
        TrinityCompressedSchedule adjustedSchedule = new TrinityCompressedSchedule(
                scheduled.schedule().batches(),
                finalBalances,
                scheduled.schedule().statesVisited());
        TrinityJointCyclePlan plan = new TrinityJointCyclePlan(
                solution.firings(),
                scheduled.externalInputs(),
                scheduled.minimumSeed(),
                initialInputs,
                accounting.netChange(),
                adjustedSchedule,
                adjustedSchedule.statesVisited(),
                solverPasses,
                solverNanos);
        TrinityLexicographicObjective objective = new TrinityLexicographicObjective(
                sum(plan.externalInputs()),
                sum(plan.minimumSeed()),
                sum(plan.firings()),
                TrinityFiringVector.from(orderedVariants, plan.firings()));
        return TrinityAlgorithmResult.success(new TrinityJointCandidateEvaluation(
                plan,
                objective,
                adjustedSchedule.statesVisited()));
    }

    private TrinityAlgorithmResult<TrinityMinimumSeedSchedule> findExecutableInputs(
                                                                                    Map<TrinityPatternVariant, BigInteger> firings,
                                                                                    Set<AEKey> externalKeys,
                                                                                    Set<AEKey> internalKeys,
                                                                                    Map<AEKey, BigInteger> minimumInputs,
                                                                                    Map<AEKey, BigInteger> maximumInputs,
                                                                                    BigInteger seedLowerBound,
                                                                                    int maxStates,
                                                                                    TrinityPlanningControl control) {
        if (seedLowerBound == null || seedLowerBound.signum() < 0) {
            throw new IllegalArgumentException("A Trinity seed lower bound cannot be negative or null");
        }
        int directStates = 0;
        // Conservation already proves a smaller total seed cannot execute, without constraining its key distribution.
        if (sum(amountsFor(minimumInputs, internalKeys)).compareTo(seedLowerBound) >= 0) {
            TrinityAlgorithmResult<TrinityCompressedSchedule> direct = this.compressedScheduler.schedule(
                    firings,
                    minimumInputs,
                    maxStates,
                    control);
            if (direct.successful()) {
                return TrinityAlgorithmResult.success(new TrinityMinimumSeedSchedule(
                        amountsFor(minimumInputs, externalKeys),
                        amountsFor(minimumInputs, internalKeys),
                        direct.value()));
            }
            directStates = diagnosticStates(direct.diagnostic());
            if (direct.diagnostic().code() != TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER) {
                return TrinityAlgorithmResult.failure(normalizeFailure(direct.diagnostic(), directStates, maxStates));
            }
        }
        int remaining = maxStates - directStates;
        if (remaining <= 0) {
            return searchLimit(maxStates, directStates);
        }

        TrinityAlgorithmResult<TrinityMinimumSeedSchedule> searched = this.seedScheduler.find(
                firings,
                externalKeys,
                internalKeys,
                minimumInputs,
                maximumInputs,
                remaining,
                control);
        if (searched.successful()) {
            int totalStates = Math.addExact(directStates, searched.value().schedule().statesVisited());
            TrinityCompressedSchedule schedule = searched.value().schedule();
            return TrinityAlgorithmResult.success(new TrinityMinimumSeedSchedule(
                    searched.value().externalInputs(),
                    searched.value().minimumSeed(),
                    new TrinityCompressedSchedule(schedule.batches(), schedule.finalBalances(), totalStates)));
        }
        int totalStates = Math.addExact(directStates, diagnosticStates(searched.diagnostic()));
        if (searched.diagnostic().code() == TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER) {
            return failure(
                    TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                    NO_ORDER_KEY,
                    Map.of("states", Integer.toString(totalStates)));
        }
        return TrinityAlgorithmResult.failure(normalizeFailure(searched.diagnostic(), totalStates, maxStates));
    }

    private static TrinityPlanningDiagnostic normalizeFailure(
                                                              TrinityPlanningDiagnostic diagnostic,
                                                              int states,
                                                              int limit) {
        if (diagnostic.code() == TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT &&
                "timeout".equals(diagnostic.metadata().get("reason"))) {
            return new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                    Component.translatable(MIP_TIMEOUT_KEY),
                    Map.of("states", Integer.toString(states)));
        }
        if (diagnostic.code() == TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT) {
            return new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                    Component.translatable(SEARCH_LIMIT_KEY),
                    Map.of("limit", Integer.toString(limit), "states", Integer.toString(states)));
        }
        return diagnostic;
    }

    private static TrinityAlgorithmResult<TrinityMinimumSeedSchedule> searchLimit(int limit, int states) {
        return failure(
                TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT,
                SEARCH_LIMIT_KEY,
                Map.of("limit", Integer.toString(limit), "states", Integer.toString(states)));
    }

    private static Optional<CandidateAccounting> accountCandidate(
                                                                  Map<TrinityPatternVariant, BigInteger> firings,
                                                                  Set<AEKey> internalKeys,
                                                                  TrinityCycleDemand demand) {
        if (firings.isEmpty()) {
            return Optional.empty();
        }
        LinkedHashMap<AEKey, BigInteger> net = new LinkedHashMap<>();
        firings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> net.merge(key, amount.multiply(count), BigInteger::add)));
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        boolean exportsInternalKey = internalKeys.stream()
                .anyMatch(demand.requiredNetChangeLowerBounds()::containsKey);
        if (internalKeys.stream().anyMatch(key -> {
            BigInteger amount = net.getOrDefault(key, BigInteger.ZERO);
            BigInteger requested = demand.requiredNetChangeLowerBounds().get(key);
            if (requested != null) {
                return amount.compareTo(requested) < 0;
            }
            return exportsInternalKey ? amount.signum() != 0 : amount.signum() < 0;
        })) {
            return Optional.empty();
        }
        for (Map.Entry<AEKey, BigInteger> bound : demand.requiredNetChangeLowerBounds().entrySet()) {
            if (net.getOrDefault(bound.getKey(), BigInteger.ZERO).compareTo(bound.getValue()) < 0) {
                return Optional.empty();
            }
        }
        LinkedHashSet<AEKey> keys = new LinkedHashSet<>(net.keySet());
        keys.addAll(internalKeys);
        keys.addAll(demand.finalBalanceLowerBounds().keySet());
        LinkedHashMap<AEKey, BigInteger> external = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> modelSeed = new LinkedHashMap<>();
        for (AEKey key : keys) {
            BigInteger finalLower = demand.finalBalanceLowerBounds().getOrDefault(key, BigInteger.ZERO);
            BigInteger required = finalLower.subtract(net.getOrDefault(key, BigInteger.ZERO)).max(BigInteger.ZERO);
            if (required.signum() > 0) {
                if (internalKeys.contains(key)) {
                    modelSeed.put(key, required);
                } else {
                    external.put(key, required);
                }
            }
        }
        return Optional.of(new CandidateAccounting(
                Collections.unmodifiableMap(external),
                Collections.unmodifiableMap(modelSeed),
                Collections.unmodifiableMap(net)));
    }

    private static Map<AEKey, BigInteger> candidateInputBounds(
                                                               Map<TrinityPatternVariant, BigInteger> firings,
                                                               Set<AEKey> externalKeys,
                                                               Set<AEKey> internalKeys,
                                                               CandidateAccounting accounting,
                                                               Map<AEKey, BigInteger> available,
                                                               Set<AEKey> producibleInputs) {
        LinkedHashSet<AEKey> injectableKeys = new LinkedHashSet<>(externalKeys);
        firings.keySet().forEach(variant -> variant.inputs().keySet().stream()
                .filter(internalKeys::contains)
                .forEach(injectableKeys::add));
        injectableKeys.addAll(accounting.requiredModelSeed().keySet());

        LinkedHashMap<AEKey, BigInteger> totalConsumption = new LinkedHashMap<>();
        firings.forEach((variant, count) -> variant.inputs().forEach(
                (key, amount) -> totalConsumption.merge(key, amount.multiply(count), BigInteger::add)));
        LinkedHashMap<AEKey, BigInteger> bounds = new LinkedHashMap<>();
        for (AEKey key : injectableKeys) {
            BigInteger bound = available.getOrDefault(key, BigInteger.ZERO);
            if (producibleInputs.contains(key)) {
                bound = bound.max(totalConsumption.getOrDefault(key, BigInteger.ZERO));
                bound = bound.max(accounting.externalInputs().getOrDefault(key, BigInteger.ZERO));
                bound = bound.max(accounting.requiredModelSeed().getOrDefault(key, BigInteger.ZERO));
            }
            bounds.put(key, bound);
        }
        return Collections.unmodifiableMap(bounds);
    }

    private static Map<AEKey, BigInteger> finiteInputUpperBounds(
                                                                 List<TrinityPatternVariant> variants,
                                                                 Set<AEKey> internalKeys,
                                                                 TrinityCycleDemand demand,
                                                                 Map<AEKey, BigInteger> available,
                                                                 Set<AEKey> producibleInputs) {
        LinkedHashSet<AEKey> inputKeys = new LinkedHashSet<>(internalKeys);
        variants.forEach(variant -> inputKeys.addAll(variant.inputs().keySet()));
        inputKeys.addAll(demand.finalBalanceLowerBounds().keySet());
        LinkedHashMap<AEKey, BigInteger> bounds = new LinkedHashMap<>();
        inputKeys.stream()
                .filter(key -> !producibleInputs.contains(key))
                .forEach(key -> bounds.put(key, available.getOrDefault(key, BigInteger.ZERO)));
        return Collections.unmodifiableMap(bounds);
    }

    private static Set<AEKey> externalReserveKeys(
                                                  List<TrinityPatternVariant> variants,
                                                  Set<AEKey> internalKeys,
                                                  TrinityCycleDemand demand) {
        LinkedHashSet<AEKey> externalKeys = new LinkedHashSet<>();
        variants.forEach(variant -> variant.inputs().keySet().stream()
                .filter(key -> !internalKeys.contains(key))
                .forEach(externalKeys::add));
        demand.finalBalanceLowerBounds().keySet().stream()
                .filter(key -> !internalKeys.contains(key))
                .forEach(externalKeys::add);
        return Collections.unmodifiableSet(externalKeys);
    }

    private static Map<AEKey, BigInteger> amountsFor(
                                                     Map<AEKey, BigInteger> amounts,
                                                     Set<AEKey> keys) {
        LinkedHashMap<AEKey, BigInteger> selected = new LinkedHashMap<>();
        keys.forEach(key -> {
            BigInteger amount = amounts.getOrDefault(key, BigInteger.ZERO);
            if (amount.signum() > 0) {
                selected.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(selected);
    }

    private static void requireExternalInputs(
                                              Map<AEKey, BigInteger> actual,
                                              Map<AEKey, BigInteger> required) {
        if (required.entrySet().stream().anyMatch(entry -> actual.getOrDefault(entry.getKey(), BigInteger.ZERO).compareTo(entry.getValue()) < 0)) {
            throw new IllegalStateException("An exact Trinity schedule lost required external input");
        }
    }

    private static boolean exceedsAvailable(
                                            Map<AEKey, BigInteger> required,
                                            Map<AEKey, BigInteger> available,
                                            Set<AEKey> producibleInputs) {
        return required.entrySet().stream().anyMatch(entry -> !producibleInputs.contains(entry.getKey()) &&
                available.getOrDefault(entry.getKey(), BigInteger.ZERO).compareTo(entry.getValue()) < 0);
    }

    private static Map<AEKey, BigInteger> addSigned(
                                                    Map<AEKey, BigInteger> initial,
                                                    Map<AEKey, BigInteger> change) {
        LinkedHashMap<AEKey, BigInteger> result = new LinkedHashMap<>(initial);
        change.forEach((key, amount) -> result.merge(key, amount, BigInteger::add));
        if (result.values().stream().anyMatch(amount -> amount.signum() < 0)) {
            throw new IllegalStateException("An exact Trinity joint cycle candidate has a negative final balance");
        }
        result.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return Collections.unmodifiableMap(result);
    }

    private static int diagnosticStates(TrinityPlanningDiagnostic diagnostic) {
        String encoded = diagnostic.metadata().get("states");
        if (encoded == null) {
            throw new IllegalStateException("A Trinity schedule diagnostic must report visited states");
        }
        try {
            int states = Integer.parseInt(encoded);
            if (states < 0) {
                throw new IllegalStateException("Trinity schedule diagnostic states cannot be negative");
            }
            return states;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Trinity schedule diagnostic states must be an integer", exception);
        }
    }

    private static BigInteger sum(Map<?, BigInteger> amounts) {
        return amounts.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
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

    private record CandidateAccounting(
                                       Map<AEKey, BigInteger> externalInputs,
                                       Map<AEKey, BigInteger> requiredModelSeed,
                                       Map<AEKey, BigInteger> netChange) {}
}
