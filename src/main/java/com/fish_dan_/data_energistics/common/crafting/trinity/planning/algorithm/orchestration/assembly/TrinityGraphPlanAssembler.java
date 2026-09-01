package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.assembly;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCycleSelection;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.TrinityAcyclicPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanContext;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.demand.TrinityGraphDemandSolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCycleRepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanByteEstimateInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanByteEstimator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanningStatistics;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts solved graph demands into compact execution stages and final immutable Trinity crafting plans.
 * <p>
 * Builds resource-safe independent stages and compressed repeat blocks from exact aggregate firing selections.
 */
public final class TrinityGraphPlanAssembler {

    /**
     * Creates a plan assembler using the shared conservative AE2 byte estimator.
     */
    public static TrinityGraphPlanAssembler create(TrinityPlanByteEstimator byteEstimator) {
        return new TrinityGraphPlanAssembler(byteEstimator);
    }

    private static final String INSUFFICIENT_INPUT_KEY = "gui.data_energistics.trinity_planning.diagnostic.insufficient_input";

    private final TrinityPlanByteEstimator byteEstimator;

    TrinityGraphPlanAssembler(TrinityPlanByteEstimator byteEstimator) {
        if (byteEstimator == null) {
            throw new IllegalArgumentException("A Trinity graph plan assembler requires a byte estimator");
        }
        this.byteEstimator = byteEstimator;
    }

    /**
     * Converts the dedicated DAG propagator result into the common plan payload.
     */
    public TrinityGraphPlanAssembly assembleAcyclic(TrinityAcyclicPlan acyclicPlan) {
        if (acyclicPlan == null) {
            throw new IllegalArgumentException("A Trinity acyclic plan assembly requires a solved plan");
        }
        ArrayList<TrinityPlanStage> stages = new ArrayList<>(acyclicPlan.executionOrder().size());
        ArrayList<Integer> stageOrder = new ArrayList<>(acyclicPlan.executionOrder().size());
        LinkedHashMap<TrinityPatternIdentity, BigInteger> patternFirings = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> stackRequests = new LinkedHashMap<>();
        for (TrinityVariantFiring firing : acyclicPlan.executionOrder()) {
            int stageIndex = stages.size();
            stages.add(stage(
                    stageIndex,
                    false,
                    firing.variant(),
                    firing.count(),
                    false));
            stageOrder.add(stageIndex);
            mergePatternFiring(patternFirings, firing.variant(), firing.count());
            mergeScaled(stackRequests, firing.variant().inputs(), firing.count());
            mergeScaled(stackRequests, firing.variant().outputs(), firing.count());
        }
        List<TrinityPlanStage> plannedStages = TrinityStageDependencyPlanner.plan(
                acyclicPlan.externalInputs(),
                stages,
                stageOrder,
                List.of());
        return new TrinityGraphPlanAssembly(
                acyclicPlan.externalInputs(),
                Collections.unmodifiableMap(patternFirings),
                plannedStages,
                List.copyOf(stageOrder),
                List.of(),
                Map.of(),
                acyclicPlan.netChange(),
                Collections.unmodifiableMap(stackRequests),
                acyclicPlan.statesVisited(),
                0L,
                acyclicPlan.quality(),
                Map.of(),
                Map.of(),
                0);
    }

    /**
     * Converts aggregate acyclic firings and selected cycle blocks into the common plan payload.
     */
    public TrinityAlgorithmResult<TrinityGraphPlanAssembly> assembleDemand(
                                                                           AEKey target,
                                                                           TrinityCraftingTopology topology,
                                                                           TrinityGraphDemandSolution demandSolution) {
        if (target == null || topology == null || demandSolution == null) {
            throw new IllegalArgumentException("A Trinity aggregate plan assembly request is incomplete");
        }
        Map<Integer, Integer> topologicalPositions = topologicalPositions(topology);
        ArrayList<OrderedUnit> units = new ArrayList<>();
        demandSolution.acyclicFirings().forEach((variant, firing) -> units.add(new AcyclicUnit(
                firing.rank(),
                variant,
                firing.count())));
        for (int index = 0; index < demandSolution.cycleSolutions().size(); index++) {
            TrinityCycleSelection cycle = demandSolution.cycleSolutions().get(index);
            units.add(new CycleUnit(
                    Math.multiplyExact(topologicalPositions.get(cycle.componentIndex()), 2),
                    index,
                    cycle));
        }
        units.sort(Comparator
                .comparingInt(OrderedUnit::rank)
                .thenComparing(OrderedUnit::stableKey));

        ArrayList<TrinityPlanStage> stages = new ArrayList<>();
        ArrayList<Integer> stageOrder = new ArrayList<>();
        ArrayList<TrinityCycleRepeatBlock> repeatBlocks = new ArrayList<>();
        LinkedHashMap<TrinityPatternIdentity, BigInteger> patternFirings = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> netChange = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> minimumSeed = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> retainedSeed = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> stackRequests = new LinkedHashMap<>();
        int seedRefinementPasses = 0;
        int repeatIndex = 0;

        for (OrderedUnit unit : units) {
            if (unit instanceof AcyclicUnit acyclic) {
                int stageIndex = stages.size();
                stages.add(stage(
                        stageIndex,
                        false,
                        acyclic.variant(),
                        acyclic.count(),
                        false));
                stageOrder.add(stageIndex);
                mergePatternFiring(patternFirings, acyclic.variant(), acyclic.count());
                mergeScaled(netChange, acyclic.variant().netChange(), acyclic.count());
                mergeScaled(stackRequests, acyclic.variant().inputs(), acyclic.count());
                mergeScaled(stackRequests, acyclic.variant().outputs(), acyclic.count());
                continue;
            }

            TrinityCycleSelection cycle = ((CycleUnit) unit).solution();
            appendOneTimeStages(
                    cycle.prefixOrder(),
                    stages,
                    stageOrder,
                    patternFirings,
                    stackRequests);
            ArrayList<Integer> blockStages = new ArrayList<>();
            for (TrinityVariantFiring batch : cycle.localOrder()) {
                int stageIndex = stages.size();
                stages.add(stage(
                        stageIndex,
                        true,
                        batch.variant(),
                        batch.count(),
                        true));
                stageOrder.add(stageIndex);
                blockStages.add(stageIndex);
                BigInteger totalCount = batch.count().multiply(cycle.repetitions());
                mergePatternFiring(patternFirings, batch.variant(), totalCount);
                mergeScaled(stackRequests, batch.variant().inputs(), totalCount);
                mergeScaled(stackRequests, batch.variant().outputs(), totalCount);
            }
            repeatBlocks.add(new TrinityCycleRepeatBlock(
                    repeatIndex++,
                    blockStages,
                    cycle.repetitions(),
                    minimumBalances(cycle.localOrder()),
                    repeatedNetChange(cycle.localOrder(), cycle.repetitions())));
            appendOneTimeStages(
                    cycle.suffixOrder(),
                    stages,
                    stageOrder,
                    patternFirings,
                    stackRequests);
            cycle.minimumSeed().forEach((key, amount) -> minimumSeed.merge(key, amount, BigInteger::max));
            cycle.retainedSeed().forEach((key, amount) -> retainedSeed.merge(key, amount, BigInteger::max));
            seedRefinementPasses = Math.addExact(seedRefinementPasses, cycle.seedRefinementPasses());
            mergeScaled(netChange, cycle.netChange(), BigInteger.ONE);
        }
        removeZeros(netChange);
        if (stages.isEmpty()) {
            return failure(
                    TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                    INSUFFICIENT_INPUT_KEY,
                    Map.of("target", target.toString()));
        }
        List<TrinityPlanStage> plannedStages = TrinityStageDependencyPlanner.plan(
                demandSolution.initialInputs(),
                stages,
                stageOrder,
                repeatBlocks);
        Map<AEKey, BigInteger> retainedSeedFinal = terminalSeedBalances(
                demandSolution.initialInputs(),
                plannedStages,
                stageOrder,
                repeatBlocks,
                retainedSeed);
        Map.Entry<AEKey, BigInteger> lostSeed = retainedSeed.entrySet().stream()
                .filter(entry -> retainedSeedFinal.getOrDefault(entry.getKey(), BigInteger.ZERO)
                        .compareTo(entry.getValue()) < 0)
                .findFirst()
                .orElse(null);
        if (lostSeed != null) {
            return failure(
                    TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                    "gui.data_energistics.trinity_planning.diagnostic.internal_error",
                    Map.of(
                            "phase", "terminal_seed_validation",
                            "key", lostSeed.getKey().toString(),
                            "required", lostSeed.getValue().toString()));
        }
        return TrinityAlgorithmResult.success(new TrinityGraphPlanAssembly(
                demandSolution.initialInputs(),
                Collections.unmodifiableMap(patternFirings),
                plannedStages,
                List.copyOf(stageOrder),
                List.copyOf(repeatBlocks),
                Collections.unmodifiableMap(minimumSeed),
                Collections.unmodifiableMap(netChange),
                Collections.unmodifiableMap(stackRequests),
                demandSolution.scheduleStates(),
                demandSolution.mipNanos(),
                demandSolution.quality(),
                Collections.unmodifiableMap(retainedSeed),
                retainedSeedFinal,
                seedRefinementPasses));
    }

    private static Map<AEKey, BigInteger> terminalSeedBalances(
                                                               Map<AEKey, BigInteger> initialInputs,
                                                               List<TrinityPlanStage> stages,
                                                               List<Integer> stageOrder,
                                                               List<TrinityCycleRepeatBlock> repeatBlocks,
                                                               Map<AEKey, BigInteger> retainedSeed) {
        if (retainedSeed.isEmpty()) {
            return Map.of();
        }
        Int2ObjectOpenHashMap<TrinityPlanStage> stagesByIndex = new Int2ObjectOpenHashMap<>();
        stages.forEach(stage -> stagesByIndex.put(stage.index(), stage));
        Int2ObjectOpenHashMap<TrinityCycleRepeatBlock> blocksByStage = new Int2ObjectOpenHashMap<>();
        repeatBlocks.forEach(block -> block.stageOrder().forEach(
                stageIndex -> blocksByStage.put(stageIndex, block)));
        IntOpenHashSet completedBlocks = new IntOpenHashSet();
        LinkedHashMap<AEKey, BigInteger> balances = new LinkedHashMap<>(initialInputs);
        for (Integer stageIndex : stageOrder) {
            TrinityPlanStage stage = stagesByIndex.get(stageIndex);
            if (!stage.cycleStage()) {
                mergeScaled(balances, stage.netChange(), BigInteger.ONE);
                continue;
            }
            TrinityCycleRepeatBlock block = blocksByStage.get(stageIndex);
            if (completedBlocks.add(block.index())) {
                mergeScaled(balances, block.netChange(), BigInteger.ONE);
            }
        }
        removeZeros(balances);
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> retainedBalances = new Object2ObjectLinkedOpenHashMap<>();
        retainedSeed.keySet().forEach(
                key -> retainedBalances.put(key, balances.getOrDefault(key, BigInteger.ZERO)));
        return Object2ObjectMaps.unmodifiable(retainedBalances);
    }

    /**
     * Applies exact byte estimation, statistics, and the final immutable plan builder.
     */
    public TrinityCraftingPlan finalizePlan(
                                            TrinityGraphPlanContext context,
                                            TrinityGraphPlanAssembly assembly) {
        if (context == null || assembly == null) {
            throw new IllegalArgumentException("A Trinity final plan assembly request is incomplete");
        }
        BigInteger bytes = this.byteEstimator.estimate(new TrinityPlanByteEstimateInput(
                assembly.stackRequests(),
                sum(assembly.patternFirings()),
                BigInteger.valueOf(assembly.stages().size())));
        long elapsedNanos = Math.max(
                assembly.mipNanos(),
                Math.max(0L, System.nanoTime() - context.startedNanos()));
        TrinityPlanningStatistics statistics = new TrinityPlanningStatistics(
                context.topology().components().size(),
                context.variants().size(),
                elapsedNanos,
                elapsedNanos,
                assembly.mipNanos(),
                assembly.scheduleStates(),
                0,
                0,
                0,
                0,
                assembly.quality(),
                assembly.retainedSeed().size(),
                sum(assembly.retainedSeed()),
                sum(assembly.retainedSeedFinal()),
                assembly.seedRefinementPasses());
        return TrinityCraftingPlan.builder()
                .finalOutput(new GenericStack(context.target(), context.requestedAmount().longValueExact()))
                .bytes(bytes)
                .multiplePaths(hasMultiplePaths(context.variants()))
                .catalogRevision(context.catalogRevision())
                .quantityMode(context.quantityMode())
                .initialExpectedInputs(assembly.initialInputs())
                .patternFirings(assembly.patternFirings())
                .stages(assembly.stages())
                .stageOrder(assembly.stageOrder())
                .cycleRepeatBlocks(assembly.repeatBlocks())
                .minimumSeed(assembly.minimumSeed())
                .targetNetChange(assembly.netChange())
                .emittedItems(Map.of())
                .diagnostics(List.of())
                .statistics(statistics)
                .build();
    }

    private static void appendOneTimeStages(
                                            List<TrinityVariantFiring> order,
                                            List<TrinityPlanStage> stages,
                                            List<Integer> stageOrder,
                                            Map<TrinityPatternIdentity, BigInteger> patternFirings,
                                            Map<AEKey, BigInteger> stackRequests) {
        for (TrinityVariantFiring batch : order) {
            int stageIndex = stages.size();
            stages.add(stage(
                    stageIndex,
                    false,
                    batch.variant(),
                    batch.count(),
                    false));
            stageOrder.add(stageIndex);
            mergePatternFiring(patternFirings, batch.variant(), batch.count());
            mergeScaled(stackRequests, batch.variant().inputs(), batch.count());
            mergeScaled(stackRequests, batch.variant().outputs(), batch.count());
        }
    }

    private static Map<AEKey, BigInteger> repeatedNetChange(
                                                            List<TrinityVariantFiring> order,
                                                            BigInteger repetitions) {
        LinkedHashMap<AEKey, BigInteger> netChange = new LinkedHashMap<>();
        order.forEach(batch -> mergeScaled(
                netChange,
                batch.variant().netChange(),
                batch.count().multiply(repetitions)));
        removeZeros(netChange);
        return Collections.unmodifiableMap(netChange);
    }

    private static Map<AEKey, BigInteger> minimumBalances(List<TrinityVariantFiring> order) {
        LinkedHashMap<AEKey, BigInteger> required = new LinkedHashMap<>();
        LinkedHashMap<AEKey, BigInteger> balances = new LinkedHashMap<>();
        for (TrinityVariantFiring firing : order) {
            requiredAtStart(firing.variant(), firing.count()).forEach((key, amount) -> {
                BigInteger deficit = amount.subtract(balances.getOrDefault(key, BigInteger.ZERO));
                if (deficit.signum() > 0) {
                    required.merge(key, deficit, BigInteger::add);
                    balances.merge(key, deficit, BigInteger::add);
                }
            });
            mergeScaled(balances, firing.variant().netChange(), firing.count());
        }
        balances.values().forEach(amount -> {
            if (amount.signum() < 0) {
                throw new IllegalStateException("A Trinity cycle unit requires an unaccounted entry balance");
            }
        });
        return Collections.unmodifiableMap(required);
    }

    private static TrinityPlanStage stage(
                                          int index,
                                          boolean cycle,
                                          TrinityPatternVariant variant,
                                          BigInteger count,
                                          boolean compressedCycleBatch) {
        Map<AEKey, BigInteger> required = compressedCycleBatch ?
                requiredAtStart(variant, count) :
                multiplyPositive(variant.inputs(), count);
        return new TrinityPlanStage(
                index,
                cycle,
                Set.of(),
                List.of(new TrinityPlanPatternFiring(
                        variant.patternIdentity(),
                        variant.primaryOutput(),
                        variant.ordinal(),
                        count,
                        variant.inputs(),
                        variant.declaredOutputs())),
                required,
                multiplySigned(variant.netChange(), count));
    }

    private static Map<AEKey, BigInteger> requiredAtStart(
                                                          TrinityPatternVariant variant,
                                                          BigInteger count) {
        LinkedHashMap<AEKey, BigInteger> required = new LinkedHashMap<>();
        variant.inputs().forEach((key, input) -> {
            BigInteger net = variant.netChange().getOrDefault(key, BigInteger.ZERO);
            BigInteger amount = net.signum() < 0 ?
                    input.add(net.negate().multiply(count.subtract(BigInteger.ONE))) :
                    input;
            required.put(key, amount);
        });
        return Collections.unmodifiableMap(required);
    }

    private static Map<AEKey, BigInteger> multiplyPositive(
                                                           Map<AEKey, BigInteger> amounts,
                                                           BigInteger multiplier) {
        LinkedHashMap<AEKey, BigInteger> result = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> result.put(key, amount.multiply(multiplier)));
        return Collections.unmodifiableMap(result);
    }

    private static Map<AEKey, BigInteger> multiplySigned(
                                                         Map<AEKey, BigInteger> amounts,
                                                         BigInteger multiplier) {
        LinkedHashMap<AEKey, BigInteger> result = new LinkedHashMap<>();
        amounts.forEach((key, amount) -> {
            BigInteger multiplied = amount.multiply(multiplier);
            if (multiplied.signum() != 0) {
                result.put(key, multiplied);
            }
        });
        return Collections.unmodifiableMap(result);
    }

    private static Map<Integer, Integer> topologicalPositions(TrinityCraftingTopology topology) {
        HashMap<Integer, Integer> positions = new HashMap<>();
        for (int position = 0; position < topology.topologicalOrder().size(); position++) {
            positions.put(topology.topologicalOrder().get(position), position);
        }
        return Collections.unmodifiableMap(positions);
    }

    private static boolean hasMultiplePaths(List<TrinityPatternVariant> variants) {
        HashMap<AEKey, Integer> producerCounts = new HashMap<>();
        for (TrinityPatternVariant variant : variants) {
            for (AEKey output : variant.outputs().keySet()) {
                int count = producerCounts.merge(output, 1, Integer::sum);
                if (count > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void mergePatternFiring(
                                           Map<TrinityPatternIdentity, BigInteger> firings,
                                           TrinityPatternVariant variant,
                                           BigInteger count) {
        firings.merge(variant.patternIdentity(), count, BigInteger::add);
    }

    private static void mergeScaled(
                                    Map<AEKey, BigInteger> target,
                                    Map<AEKey, BigInteger> source,
                                    BigInteger multiplier) {
        source.forEach((key, amount) -> target.merge(key, amount.multiply(multiplier), BigInteger::add));
    }

    private static void removeZeros(Map<AEKey, BigInteger> amounts) {
        amounts.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
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

    private sealed interface OrderedUnit permits AcyclicUnit, CycleUnit {

        int rank();

        String stableKey();
    }

    private record AcyclicUnit(
                               int rank,
                               TrinityPatternVariant variant,
                               BigInteger count)
            implements OrderedUnit {

        @Override
        public String stableKey() {
            return "0:" + this.variant.patternIdentity().publicationEncoding() + ':' + this.variant.ordinal();
        }
    }

    private record CycleUnit(
                             int rank,
                             int sequence,
                             TrinityCycleSelection solution)
            implements OrderedUnit {

        @Override
        public String stableKey() {
            return "1:" + String.format("%010d", this.sequence);
        }
    }
}
