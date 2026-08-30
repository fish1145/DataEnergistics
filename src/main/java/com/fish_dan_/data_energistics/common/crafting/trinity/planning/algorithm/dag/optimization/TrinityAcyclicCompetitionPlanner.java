package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.optimization;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.TrinityAcyclicPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Proves when first-level acyclic competition regions are independent, solves them separately, and exactly verifies
 * their merged execution. Any uncertainty is represented as no attempt so the caller can retain whole-graph behavior.
 */
public final class TrinityAcyclicCompetitionPlanner {

    public static TrinityAcyclicCompetitionPlanner create(TrinityAcyclicRouteOptimizer routeOptimizer) {
        return new TrinityAcyclicCompetitionPlanner(routeOptimizer);
    }

    private final TrinityAcyclicRouteOptimizer routeOptimizer;

    private TrinityAcyclicCompetitionPlanner(TrinityAcyclicRouteOptimizer routeOptimizer) {
        if (routeOptimizer == null) {
            throw new IllegalArgumentException("A Trinity competition planner requires a route optimizer");
        }
        this.routeOptimizer = routeOptimizer;
    }

    /**
     * Attempts conservative partitioning. Once local solving starts, failures use the reserved whole-graph budget and
     * return that optimizer result so the caller performs shortage diagnosis exactly once.
     */
    public Optional<Attempt> plan(
                                  TrinityCraftingTopology topology,
                                  List<TrinityPatternVariant> planningVariants,
                                  Map<AEKey, List<TrinityPatternVariant>> producers,
                                  AEKey target,
                                  BigInteger requestedAmount,
                                  CraftingQuantityMode quantityMode,
                                  Map<AEKey, BigInteger> available,
                                  int maxSearchStates,
                                  TrinityPlanningMode mode,
                                  TrinityPlanningControl control) {
        Preparation preparation = prepare(
                topology,
                producers,
                target,
                requestedAmount,
                quantityMode,
                available);
        if (preparation == null || preparation.frontiers().size() < 2) {
            return Optional.empty();
        }
        List<CompetitionRegion> regions = regions(preparation.frontiers(), producers);
        if (!provablyIndependent(regions, preparation)) {
            return Optional.empty();
        }

        int wholeGraphBudget = routePassUpperBound(planningVariants.size());
        long localBudget = regions.stream()
                .mapToLong(region -> routePassUpperBound(region.variants().size()))
                .sum();
        if (localBudget + wholeGraphBudget + 1L > maxSearchStates) {
            return Optional.empty();
        }

        LinkedHashMap<TrinityPatternVariant, BigInteger> firings = new LinkedHashMap<>(
                preparation.deterministicFirings());
        LinkedHashMap<AEKey, BigInteger> externalInputs = new LinkedHashMap<>(preparation.reservedInputs());
        TrinityPlanQuality quality = TrinityPlanQuality.PROVED_OPTIMAL;
        int states = 0;
        for (CompetitionRegion region : regions) {
            if (control.cancellationRequested()) {
                return Optional.of(new Attempt(cancelled(), wholeGraphBudget));
            }
            TrinityAlgorithmResult<TrinityAcyclicPlan> local = this.routeOptimizer.optimize(
                    topology,
                    region.variants(),
                    region.target(),
                    region.amount(),
                    CraftingQuantityMode.NET_NEW,
                    projectInventory(preparation.remainingInventory(), region.touchedKeys()),
                    routePassUpperBound(region.variants().size()),
                    mode,
                    control);
            if (!local.successful()) {
                if (local.diagnostic().code() == TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED) {
                    return Optional.of(new Attempt(local, wholeGraphBudget));
                }
                return Optional.of(new Attempt(optimizeWholeGraph(
                        topology,
                        planningVariants,
                        target,
                        requestedAmount,
                        quantityMode,
                        available,
                        wholeGraphBudget,
                        mode,
                        control), wholeGraphBudget));
            }
            local.value().firings().forEach((variant, count) -> firings.merge(variant, count, BigInteger::add));
            local.value().externalInputs().forEach(
                    (key, amount) -> externalInputs.merge(key, amount, BigInteger::add));
            quality = quality.combine(local.value().quality());
            states = Math.addExact(states, local.value().statesVisited());
        }

        TrinityAcyclicPlan combined = verifyCombined(
                topology,
                planningVariants,
                target,
                requestedAmount,
                quantityMode,
                available,
                firings,
                externalInputs,
                states,
                quality);
        if (combined == null) {
            return Optional.of(new Attempt(optimizeWholeGraph(
                    topology,
                    planningVariants,
                    target,
                    requestedAmount,
                    quantityMode,
                    available,
                    wholeGraphBudget,
                    mode,
                    control), wholeGraphBudget));
        }
        if (control.cancellationRequested()) {
            return Optional.of(new Attempt(cancelled(), wholeGraphBudget));
        }
        return Optional.of(new Attempt(TrinityAlgorithmResult.success(combined), wholeGraphBudget));
    }

    private TrinityAlgorithmResult<TrinityAcyclicPlan> optimizeWholeGraph(
                                                                          TrinityCraftingTopology topology,
                                                                          List<TrinityPatternVariant> variants,
                                                                          AEKey target,
                                                                          BigInteger requestedAmount,
                                                                          CraftingQuantityMode quantityMode,
                                                                          Map<AEKey, BigInteger> available,
                                                                          int maxSearchStates,
                                                                          TrinityPlanningMode mode,
                                                                          TrinityPlanningControl control) {
        return this.routeOptimizer.optimize(
                topology,
                variants,
                target,
                requestedAmount,
                quantityMode,
                available,
                maxSearchStates,
                mode,
                control);
    }

    private static Preparation prepare(
                                       TrinityCraftingTopology topology,
                                       Map<AEKey, List<TrinityPatternVariant>> producers,
                                       AEKey target,
                                       BigInteger requestedAmount,
                                       CraftingQuantityMode quantityMode,
                                       Map<AEKey, BigInteger> available) {
        LinkedHashMap<AEKey, BigInteger> inventory = copyAvailable(available);
        LinkedHashMap<AEKey, BigInteger> need = new LinkedHashMap<>();
        merge(need, target, requestedAmount);
        LinkedHashMap<TrinityPatternVariant, BigInteger> deterministicFirings = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> reservedInputs = new LinkedHashMap<>();
        LinkedHashMap<AEKey, FrontierDemand> frontiers = new LinkedHashMap<>();
        LinkedHashSet<AEKey> deterministicTouchedKeys = new LinkedHashSet<>();
        LinkedHashSet<TrinityPatternIdentity> deterministicPatterns = new LinkedHashSet<>();

        List<Integer> componentOrder = topology.topologicalOrder();
        for (int position = componentOrder.size() - 1; position >= 0; position--) {
            TrinityStronglyConnectedComponent component = topology.components().get(componentOrder.get(position));
            for (AEKey key : component.keys()) {
                BigInteger required = need.getOrDefault(key, BigInteger.ZERO);
                boolean forceFinalTotalProduction = key.equals(target) &&
                        quantityMode == CraftingQuantityMode.FINAL_TOTAL;
                if (required.signum() <= 0 && !forceFinalTotalProduction) {
                    continue;
                }
                BigInteger availableAmount = inventory.getOrDefault(key, BigInteger.ZERO);
                BigInteger reserved = key.equals(target) && quantityMode == CraftingQuantityMode.NET_NEW ?
                        BigInteger.ZERO : required.max(BigInteger.ZERO).min(availableAmount);
                if (reserved.signum() > 0) {
                    reservedInputs.merge(key, reserved, BigInteger::add);
                    inventory.put(key, availableAmount.subtract(reserved));
                    merge(need, key, reserved.negate());
                    deterministicTouchedKeys.add(key);
                }
                BigInteger missing = need.getOrDefault(key, BigInteger.ZERO).max(BigInteger.ZERO);
                if (missing.signum() <= 0 && !forceFinalTotalProduction) {
                    continue;
                }
                List<TrinityPatternVariant> candidates = producers.getOrDefault(key, List.of());
                if (candidates.isEmpty()) {
                    return null;
                }
                if (candidates.size() > 1 || candidates.stream().anyMatch(variant -> variant.outputs().size() > 1)) {
                    frontiers.put(key, new FrontierDemand(key, missing.signum() > 0 ? missing : BigInteger.ONE));
                    continue;
                }

                TrinityPatternVariant selected = candidates.getFirst();
                BigInteger count = missing.signum() > 0 ?
                        ceilDivide(missing, selected.outputs().get(key)) : BigInteger.ONE;
                deterministicFirings.merge(selected, count, BigInteger::add);
                deterministicPatterns.add(selected.patternIdentity());
                deterministicTouchedKeys.addAll(selected.inputs().keySet());
                deterministicTouchedKeys.addAll(selected.outputs().keySet());
                selected.inputs().forEach((input, amount) -> merge(need, input, amount.multiply(count)));
                selected.outputs().forEach((output, amount) -> merge(need, output, amount.multiply(count).negate()));
            }
        }
        return new Preparation(
                List.copyOf(frontiers.values()),
                deterministicFirings,
                reservedInputs,
                inventory,
                Set.copyOf(deterministicTouchedKeys),
                Set.copyOf(deterministicPatterns));
    }

    private static List<CompetitionRegion> regions(
                                                   List<FrontierDemand> frontiers,
                                                   Map<AEKey, List<TrinityPatternVariant>> producers) {
        ArrayList<CompetitionRegion> regions = new ArrayList<>(frontiers.size());
        for (FrontierDemand frontier : frontiers) {
            ArrayList<AEKey> pending = new ArrayList<>();
            LinkedHashSet<AEKey> visitedKeys = new LinkedHashSet<>();
            LinkedHashSet<TrinityPatternVariant> regionVariants = new LinkedHashSet<>();
            pending.add(frontier.key());
            for (int index = 0; index < pending.size(); index++) {
                AEKey key = pending.get(index);
                if (!visitedKeys.add(key)) {
                    continue;
                }
                for (TrinityPatternVariant producer : producers.getOrDefault(key, List.of())) {
                    if (regionVariants.add(producer)) {
                        pending.addAll(producer.inputs().keySet());
                    }
                }
            }
            ArrayList<TrinityPatternVariant> ordered = new ArrayList<>(regionVariants);
            ordered.sort(Comparator.naturalOrder());
            LinkedHashSet<AEKey> touchedKeys = new LinkedHashSet<>();
            LinkedHashSet<TrinityPatternIdentity> patterns = new LinkedHashSet<>();
            for (TrinityPatternVariant variant : ordered) {
                patterns.add(variant.patternIdentity());
                touchedKeys.addAll(variant.inputs().keySet());
                touchedKeys.addAll(variant.outputs().keySet());
            }
            regions.add(new CompetitionRegion(
                    frontier.key(),
                    frontier.amount(),
                    List.copyOf(ordered),
                    Set.copyOf(touchedKeys),
                    Set.copyOf(patterns)));
        }
        return List.copyOf(regions);
    }

    private static boolean provablyIndependent(List<CompetitionRegion> regions, Preparation preparation) {
        boolean reservedCraftableSuffix = preparation.deterministicFirings().keySet().stream()
                .flatMap(variant -> variant.outputs().keySet().stream())
                .anyMatch(preparation.reservedInputs()::containsKey);
        if (reservedCraftableSuffix) {
            return false;
        }
        LinkedHashSet<AEKey> occupiedKeys = new LinkedHashSet<>();
        LinkedHashSet<TrinityPatternIdentity> occupiedPatterns = new LinkedHashSet<>();
        for (CompetitionRegion region : regions) {
            if (region.variants().isEmpty() ||
                    preparation.reservedInputs().containsKey(region.target()) ||
                    region.patterns().stream().anyMatch(preparation.deterministicPatterns()::contains)) {
                return false;
            }
            for (AEKey key : region.touchedKeys()) {
                if (preparation.deterministicTouchedKeys().contains(key) && !key.equals(region.target())) {
                    return false;
                }
            }
            if (region.touchedKeys().stream().anyMatch(key -> !occupiedKeys.add(key)) ||
                    region.patterns().stream().anyMatch(pattern -> !occupiedPatterns.add(pattern))) {
                return false;
            }
        }
        return true;
    }

    private static TrinityAcyclicPlan verifyCombined(
                                                     TrinityCraftingTopology topology,
                                                     List<TrinityPatternVariant> legalVariants,
                                                     AEKey target,
                                                     BigInteger requestedAmount,
                                                     CraftingQuantityMode quantityMode,
                                                     Map<AEKey, BigInteger> available,
                                                     Map<TrinityPatternVariant, BigInteger> firings,
                                                     Map<AEKey, BigInteger> externalInputs,
                                                     int states,
                                                     TrinityPlanQuality quality) {
        Set<TrinityPatternVariant> legal = new HashSet<>(legalVariants);
        if (firings.isEmpty() || firings.entrySet().stream().anyMatch(
                entry -> !legal.contains(entry.getKey()) || entry.getValue().signum() <= 0)) {
            return null;
        }
        for (Map.Entry<AEKey, BigInteger> input : externalInputs.entrySet()) {
            if (input.getValue().signum() <= 0 ||
                    input.getValue().compareTo(available.getOrDefault(input.getKey(), BigInteger.ZERO)) > 0 ||
                    quantityMode == CraftingQuantityMode.NET_NEW && input.getKey().equals(target)) {
                return null;
            }
        }

        LinkedHashMap<AEKey, BigInteger> net = aggregateNetChange(firings);
        LinkedHashSet<AEKey> balanceKeys = new LinkedHashSet<>(net.keySet());
        balanceKeys.addAll(externalInputs.keySet());
        for (AEKey key : balanceKeys) {
            if (externalInputs.getOrDefault(key, BigInteger.ZERO)
                    .add(net.getOrDefault(key, BigInteger.ZERO)).signum() < 0) {
                return null;
            }
        }
        BigInteger targetNet = net.getOrDefault(target, BigInteger.ZERO);
        if (targetNet.signum() <= 0 ||
                quantityMode == CraftingQuantityMode.NET_NEW && targetNet.compareTo(requestedAmount) < 0 ||
                quantityMode == CraftingQuantityMode.FINAL_TOTAL &&
                        externalInputs.getOrDefault(target, BigInteger.ZERO)
                                .add(targetNet).compareTo(requestedAmount) < 0) {
            return null;
        }

        Map<Integer, Integer> positions = topologicalPositions(topology);
        ArrayList<TrinityVariantFiring> executionOrder = new ArrayList<>();
        firings.entrySet().stream()
                .sorted(Comparator
                        .comparingInt((Map.Entry<TrinityPatternVariant, BigInteger> entry) -> producerPosition(
                                topology,
                                positions,
                                entry.getKey()))
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> executionOrder.add(new TrinityVariantFiring(entry.getKey(), entry.getValue())));
        if (!executionPrefixNonNegative(executionOrder, externalInputs)) {
            return null;
        }
        LinkedHashMap<TrinityPatternVariant, BigInteger> orderedFirings = new LinkedHashMap<>();
        executionOrder.forEach(firing -> orderedFirings.put(firing.variant(), firing.count()));
        return new TrinityAcyclicPlan(
                executionOrder,
                orderedFirings,
                externalInputs,
                net,
                states,
                quality);
    }

    private static Map<AEKey, BigInteger> projectInventory(
                                                           Map<AEKey, BigInteger> inventory,
                                                           Set<AEKey> touchedKeys) {
        LinkedHashMap<AEKey, BigInteger> projected = new LinkedHashMap<>();
        touchedKeys.forEach(key -> {
            BigInteger amount = inventory.getOrDefault(key, BigInteger.ZERO);
            if (amount.signum() > 0) {
                projected.put(key, amount);
            }
        });
        return projected;
    }

    private static boolean executionPrefixNonNegative(
                                                      List<TrinityVariantFiring> executionOrder,
                                                      Map<AEKey, BigInteger> externalInputs) {
        LinkedHashMap<AEKey, BigInteger> balance = new LinkedHashMap<>(externalInputs);
        for (TrinityVariantFiring firing : executionOrder) {
            for (Map.Entry<AEKey, BigInteger> input : firing.variant().inputs().entrySet()) {
                BigInteger required = input.getValue().multiply(firing.count());
                BigInteger present = balance.getOrDefault(input.getKey(), BigInteger.ZERO);
                if (present.compareTo(required) < 0) {
                    return false;
                }
                balance.put(input.getKey(), present.subtract(required));
            }
            firing.variant().outputs().forEach((key, amount) -> balance.merge(
                    key,
                    amount.multiply(firing.count()),
                    BigInteger::add));
        }
        return true;
    }

    private static LinkedHashMap<AEKey, BigInteger> aggregateNetChange(
                                                                       Map<TrinityPatternVariant, BigInteger> firings) {
        LinkedHashMap<AEKey, BigInteger> net = new LinkedHashMap<>();
        firings.forEach((variant, count) -> variant.netChange().forEach(
                (key, amount) -> net.merge(key, amount.multiply(count), BigInteger::add)));
        net.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        return net;
    }

    private static Map<Integer, Integer> topologicalPositions(TrinityCraftingTopology topology) {
        HashMap<Integer, Integer> positions = new HashMap<>();
        for (int position = 0; position < topology.topologicalOrder().size(); position++) {
            positions.put(topology.topologicalOrder().get(position), position);
        }
        return positions;
    }

    private static int producerPosition(TrinityCraftingTopology topology,
                                        Map<Integer, Integer> positions,
                                        TrinityPatternVariant variant) {
        int earliestOutput = Integer.MAX_VALUE;
        for (AEKey output : variant.outputs().keySet()) {
            Integer component = topology.componentByKey().get(output);
            if (component != null) {
                earliestOutput = Math.min(earliestOutput, positions.get(component));
            }
        }
        return earliestOutput;
    }

    private static LinkedHashMap<AEKey, BigInteger> copyAvailable(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (amount.signum() > 0) {
                copied.put(key, amount);
            }
        });
        return copied;
    }

    private static BigInteger ceilDivide(BigInteger numerator, BigInteger denominator) {
        BigInteger[] division = numerator.divideAndRemainder(denominator);
        return division[1].signum() == 0 ? division[0] : division[0].add(BigInteger.ONE);
    }

    private static void merge(Map<AEKey, BigInteger> amounts, AEKey key, BigInteger amount) {
        amounts.merge(key, amount, BigInteger::add);
    }

    private static int routePassUpperBound(int variantCount) {
        return Math.addExact(variantCount, 1);
    }

    private static TrinityAlgorithmResult<TrinityAcyclicPlan> cancelled() {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                Component.translatable("gui.data_energistics.trinity_planning.diagnostic.cancelled"),
                Map.of("phase", "dag_competition")));
    }

    /** Result from either a verified partition or its state-budgeted whole-graph fallback. */
    public record Attempt(TrinityAlgorithmResult<TrinityAcyclicPlan> result, int diagnosticBudget) {

        public Attempt {
            if (result == null || diagnosticBudget <= 0) {
                throw new IllegalArgumentException("A Trinity competition attempt requires a result and budget");
            }
        }
    }

    private record FrontierDemand(AEKey key, BigInteger amount) {}

    private record CompetitionRegion(
                                     AEKey target,
                                     BigInteger amount,
                                     List<TrinityPatternVariant> variants,
                                     Set<AEKey> touchedKeys,
                                     Set<TrinityPatternIdentity> patterns) {}

    private record Preparation(
                               List<FrontierDemand> frontiers,
                               Map<TrinityPatternVariant, BigInteger> deterministicFirings,
                               Map<AEKey, BigInteger> reservedInputs,
                               Map<AEKey, BigInteger> remainingInventory,
                               Set<AEKey> deterministicTouchedKeys,
                               Set<TrinityPatternIdentity> deterministicPatterns) {}
}
