package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Fully validated immutable implementation of the compact Trinity-only crafting plan.
 */
public final class TrinityCraftingPlanImpl implements TrinityCraftingPlan {

    private final GenericStack finalOutput;
    private final long bytes;
    private final boolean multiplePaths;
    private final long catalogRevision;
    private final CraftingQuantityMode quantityMode;
    private final Map<AEKey, BigInteger> initialExpectedInputs;
    private final Map<TrinityPatternIdentity, BigInteger> patternFirings;
    private final Map<AEKey, BigInteger> plannedOutputs;
    private final List<TrinityPlanStage> stages;
    private final List<Integer> stageOrder;
    private final List<TrinityCycleRepeatBlock> cycleRepeatBlocks;
    private final Map<AEKey, BigInteger> minimumSeed;
    private final Map<AEKey, BigInteger> targetNetChange;
    private final List<TrinityPlanningDiagnostic> diagnostics;
    private final TrinityPlanningStatistics statistics;
    private final KeyCounter usedItems;
    private final KeyCounter emittedItems;

    private TrinityCraftingPlanImpl(Builder builder) {
        if (builder.finalOutput == null || builder.finalOutput.what() == null || builder.finalOutput.amount() <= 0L) {
            throw new IllegalStateException("A Trinity plan requires a positive final output");
        }
        if (builder.bytes < 0L || builder.catalogRevision < 0L || builder.quantityMode == null) {
            throw new IllegalStateException("A Trinity plan requires bytes, revision and quantity mode");
        }

        this.finalOutput = builder.finalOutput;
        this.bytes = builder.bytes;
        this.multiplePaths = builder.multiplePaths;
        this.catalogRevision = builder.catalogRevision;
        this.quantityMode = builder.quantityMode;
        this.initialExpectedInputs = TrinityPlanAmounts.copyPositive(
                builder.initialExpectedInputs,
                "initial expected input");
        this.patternFirings = copyPatternFirings(builder.patternFirings);
        this.stages = copyStages(builder.stages);
        this.stageOrder = validateStageOrder(builder.stageOrder, this.stages);
        this.cycleRepeatBlocks = validateRepeatBlocks(builder.cycleRepeatBlocks, this.stages);
        this.plannedOutputs = calculatePlannedOutputs(this.stages, this.cycleRepeatBlocks);
        this.minimumSeed = TrinityPlanAmounts.copyPositive(builder.minimumSeed, "minimum seed");
        this.targetNetChange = TrinityPlanAmounts.copySignedNonZero(builder.targetNetChange, "target net change");
        this.diagnostics = copyDiagnostics(builder.diagnostics);
        this.statistics = builder.statistics;

        validateFiringAggregation(this.patternFirings, this.stages, this.cycleRepeatBlocks);
        validateNetChange(this.targetNetChange, this.stages, this.cycleRepeatBlocks);
        validateGlobalMinimumSeed(this.minimumSeed, this.cycleRepeatBlocks);
        validateExecutionBalances(
                this.initialExpectedInputs,
                this.stages,
                this.stageOrder,
                this.cycleRepeatBlocks);
        validateTargetSemantics();
        if (!this.plannedOutputs.containsKey(this.finalOutput.what())) {
            throw new IllegalArgumentException("A Trinity plan must schedule its final output");
        }

        this.usedItems = TrinityPlanAmounts.toKeyCounter(this.initialExpectedInputs);
        this.emittedItems = TrinityPlanAmounts.toKeyCounter(TrinityPlanAmounts.copyPositive(
                builder.emittedItems,
                "emitted item"));
    }

    /**
     * @return empty large-object builder used by graph planners and persistence migration
     */
    public static Builder builder() {
        return new Builder();
    }

    private static Map<TrinityPatternIdentity, BigInteger> copyPatternFirings(
                                                                              Map<TrinityPatternIdentity, BigInteger> source) {
        if (source == null || source.isEmpty()) {
            throw new IllegalStateException("A Trinity plan requires at least one pattern firing");
        }
        TreeMap<TrinityPatternIdentity, BigInteger> copied = new TreeMap<>();
        source.forEach((identity, count) -> {
            if (identity == null || count == null || count.signum() <= 0) {
                throw new IllegalArgumentException("Trinity aggregate pattern firings must be positive");
            }
            copied.put(identity, count);
        });
        return Collections.unmodifiableMap(copied);
    }

    private static List<TrinityPlanStage> copyStages(List<TrinityPlanStage> source) {
        if (source == null || source.isEmpty()) {
            throw new IllegalStateException("A Trinity plan requires at least one stage");
        }
        ArrayList<TrinityPlanStage> copied = new ArrayList<>(source);
        Set<Integer> indexes = new HashSet<>();
        for (TrinityPlanStage stage : copied) {
            if (stage == null || !indexes.add(stage.index())) {
                throw new IllegalArgumentException("A Trinity plan requires unique non-null stage indexes");
            }
        }
        for (TrinityPlanStage stage : copied) {
            if (!indexes.containsAll(stage.dependencies())) {
                throw new IllegalArgumentException("A Trinity stage dependency is absent from the plan");
            }
        }
        return List.copyOf(copied);
    }

    private static List<Integer> validateStageOrder(List<Integer> source, List<TrinityPlanStage> stages) {
        if (source == null || source.size() != stages.size()) {
            throw new IllegalStateException("A Trinity plan stage order must contain every stage");
        }
        Map<Integer, TrinityPlanStage> byIndex = new HashMap<>();
        stages.forEach(stage -> byIndex.put(stage.index(), stage));
        HashSet<Integer> completed = new HashSet<>();
        ArrayList<Integer> copied = new ArrayList<>(source.size());
        for (Integer index : source) {
            TrinityPlanStage stage = byIndex.get(index);
            if (stage == null || !completed.add(index) || !completed.containsAll(stage.dependencies())) {
                throw new IllegalArgumentException("A Trinity plan stage order must be complete and topological");
            }
            copied.add(index);
        }
        return List.copyOf(copied);
    }

    private static List<TrinityCycleRepeatBlock> validateRepeatBlocks(
                                                                      List<TrinityCycleRepeatBlock> source,
                                                                      List<TrinityPlanStage> stages) {
        if (source == null) {
            throw new IllegalArgumentException("Trinity repeat blocks are required");
        }
        Set<Integer> cycleStages = new HashSet<>();
        stages.stream().filter(TrinityPlanStage::cycleStage).forEach(stage -> cycleStages.add(stage.index()));
        HashSet<Integer> usedStages = new HashSet<>();
        HashSet<Integer> blockIndexes = new HashSet<>();
        for (TrinityCycleRepeatBlock block : source) {
            if (block == null || !blockIndexes.add(block.index()) ||
                    !cycleStages.containsAll(block.stageOrder())) {
                throw new IllegalArgumentException("A Trinity repeat block must reference unique cycle stages");
            }
            for (Integer stage : block.stageOrder()) {
                if (!usedStages.add(stage)) {
                    throw new IllegalArgumentException("A Trinity cycle stage cannot belong to multiple repeat blocks");
                }
            }
        }
        if (!usedStages.equals(cycleStages)) {
            throw new IllegalArgumentException("Every Trinity cycle stage must belong to exactly one repeat block");
        }
        return List.copyOf(source);
    }

    private static List<TrinityPlanningDiagnostic> copyDiagnostics(List<TrinityPlanningDiagnostic> source) {
        if (source == null) {
            throw new IllegalArgumentException("Trinity planning diagnostics are required");
        }
        for (TrinityPlanningDiagnostic diagnostic : source) {
            if (diagnostic == null) {
                throw new IllegalArgumentException("A Trinity plan cannot contain a null diagnostic");
            }
        }
        return List.copyOf(source);
    }

    private static void validateFiringAggregation(
                                                  Map<TrinityPatternIdentity, BigInteger> expected,
                                                  List<TrinityPlanStage> stages,
                                                  List<TrinityCycleRepeatBlock> repeatBlocks) {
        Map<Integer, BigInteger> stageMultipliers = new HashMap<>();
        stages.forEach(stage -> stageMultipliers.put(stage.index(), BigInteger.ONE));
        for (TrinityCycleRepeatBlock block : repeatBlocks) {
            block.stageOrder().forEach(stage -> stageMultipliers.put(stage, block.repetitions()));
        }
        Map<TrinityPatternIdentity, BigInteger> actual = new TreeMap<>();
        for (TrinityPlanStage stage : stages) {
            for (TrinityPlanPatternFiring firing : stage.firings()) {
                BigInteger total = firing.count().multiply(stageMultipliers.get(stage.index()));
                actual.merge(firing.patternIdentity(), total, BigInteger::add);
            }
        }
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("Trinity aggregate pattern firings do not match stage firings");
        }
    }

    private static Map<AEKey, BigInteger> calculatePlannedOutputs(
                                                                  List<TrinityPlanStage> stages,
                                                                  List<TrinityCycleRepeatBlock> repeatBlocks) {
        Map<Integer, BigInteger> stageMultipliers = new HashMap<>();
        stages.forEach(stage -> stageMultipliers.put(stage.index(), BigInteger.ONE));
        repeatBlocks.forEach(block -> block.stageOrder().forEach(
                stage -> stageMultipliers.put(stage, block.repetitions())));

        LinkedHashMap<AEKey, BigInteger> outputs = new LinkedHashMap<>();
        for (TrinityPlanStage stage : stages) {
            BigInteger stageMultiplier = stageMultipliers.get(stage.index());
            for (TrinityPlanPatternFiring firing : stage.firings()) {
                BigInteger totalFirings = firing.count().multiply(stageMultiplier);
                firing.outputs().forEach((key, amount) -> outputs.merge(
                        key,
                        amount.multiply(totalFirings),
                        BigInteger::add));
            }
        }
        outputs.replaceAll((key, amount) -> BigInteger.valueOf(amount.longValueExact()));
        return TrinityPlanAmounts.copyPositive(outputs, "planned output");
    }

    private static void validateNetChange(
                                          Map<AEKey, BigInteger> expected,
                                          List<TrinityPlanStage> stages,
                                          List<TrinityCycleRepeatBlock> repeatBlocks) {
        Map<Integer, TrinityPlanStage> stagesByIndex = new HashMap<>();
        stages.forEach(stage -> stagesByIndex.put(stage.index(), stage));
        LinkedHashMap<AEKey, BigInteger> actual = new LinkedHashMap<>();
        stages.stream()
                .filter(stage -> !stage.cycleStage())
                .forEach(stage -> mergeNet(actual, stage.netChange(), BigInteger.ONE));
        for (TrinityCycleRepeatBlock block : repeatBlocks) {
            LinkedHashMap<AEKey, BigInteger> expectedBlock = new LinkedHashMap<>();
            for (Integer stageIndex : block.stageOrder()) {
                mergeNet(expectedBlock, stagesByIndex.get(stageIndex).netChange(), block.repetitions());
            }
            removeZeros(expectedBlock);
            if (!expectedBlock.equals(block.netChange())) {
                throw new IllegalArgumentException(
                        "Trinity repeat block net change must equal its one-cycle stages times repetitions");
            }
            mergeNet(actual, block.netChange(), BigInteger.ONE);
        }
        removeZeros(actual);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("Trinity target net change must equal all plan stages");
        }
    }

    private static void mergeNet(
                                 Map<AEKey, BigInteger> target,
                                 Map<AEKey, BigInteger> change,
                                 BigInteger multiplier) {
        change.forEach((key, amount) -> target.merge(
                key,
                amount.multiply(multiplier),
                BigInteger::add));
    }

    private static void removeZeros(Map<AEKey, BigInteger> amounts) {
        amounts.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
    }

    private static void validateGlobalMinimumSeed(
                                                  Map<AEKey, BigInteger> expected,
                                                  List<TrinityCycleRepeatBlock> repeatBlocks) {
        if (repeatBlocks.isEmpty() && !expected.isEmpty()) {
            throw new IllegalArgumentException("A Trinity plan without cycle blocks cannot declare a minimum seed");
        }
    }

    private static void validateExecutionBalances(
                                                  Map<AEKey, BigInteger> initialInputs,
                                                  List<TrinityPlanStage> stages,
                                                  List<Integer> stageOrder,
                                                  List<TrinityCycleRepeatBlock> repeatBlocks) {
        Map<Integer, TrinityPlanStage> stagesByIndex = new HashMap<>();
        stages.forEach(stage -> stagesByIndex.put(stage.index(), stage));
        Map<Integer, TrinityCycleRepeatBlock> blockByStage = new HashMap<>();
        repeatBlocks.forEach(block -> block.stageOrder().forEach(
                stage -> blockByStage.put(stage, block)));
        HashSet<Integer> completedBlocks = new HashSet<>();
        LinkedHashMap<AEKey, BigInteger> balances = new LinkedHashMap<>(initialInputs);
        for (Integer stageIndex : stageOrder) {
            TrinityPlanStage stage = stagesByIndex.get(stageIndex);
            if (!stage.cycleStage()) {
                requireBalances(balances, stage.requiredAtStart(), "stage " + stageIndex);
                applyChange(balances, stage.netChange(), "stage " + stageIndex);
                continue;
            }
            TrinityCycleRepeatBlock block = blockByStage.get(stageIndex);
            if (!completedBlocks.add(block.index())) {
                continue;
            }
            if (!block.stageOrder().getFirst().equals(stageIndex)) {
                throw new IllegalArgumentException(
                        "A Trinity repeat block must first appear in its declared stage order");
            }
            requireBalances(balances, block.minimumSeed(), "repeat block " + block.index());
            applyChange(balances, block.netChange(), "repeat block " + block.index());
        }
    }

    private static void requireBalances(
                                        Map<AEKey, BigInteger> balances,
                                        Map<AEKey, BigInteger> required,
                                        String step) {
        required.forEach((key, amount) -> {
            if (balances.getOrDefault(key, BigInteger.ZERO).compareTo(amount) < 0) {
                throw new IllegalArgumentException("Trinity " + step + " starts without its required balance");
            }
        });
    }

    private static void applyChange(
                                    Map<AEKey, BigInteger> balances,
                                    Map<AEKey, BigInteger> change,
                                    String step) {
        change.forEach((key, amount) -> balances.merge(key, amount, BigInteger::add));
        if (balances.values().stream().anyMatch(amount -> amount.signum() < 0)) {
            throw new IllegalArgumentException("Trinity " + step + " produces a negative balance");
        }
        removeZeros(balances);
    }

    private void validateTargetSemantics() {
        BigInteger targetDelta = this.targetNetChange.getOrDefault(this.finalOutput.what(), BigInteger.ZERO);
        if (targetDelta.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity plan must have a positive target net change");
        }
        if (this.quantityMode == CraftingQuantityMode.NET_NEW &&
                targetDelta.compareTo(BigInteger.valueOf(this.finalOutput.amount())) < 0) {
            throw new IllegalArgumentException("A NET_NEW Trinity plan must produce the complete requested amount");
        }
        if (this.quantityMode == CraftingQuantityMode.FINAL_TOTAL) {
            BigInteger initialTarget = this.initialExpectedInputs.getOrDefault(
                    this.finalOutput.what(),
                    BigInteger.ZERO);
            if (initialTarget.add(targetDelta).compareTo(BigInteger.valueOf(this.finalOutput.amount())) < 0) {
                throw new IllegalArgumentException(
                        "A FINAL_TOTAL Trinity plan must reserve and produce the complete requested amount");
            }
        }
    }

    @Override
    public GenericStack finalOutput() {
        return this.finalOutput;
    }

    @Override
    public long bytes() {
        return this.bytes;
    }

    @Override
    public boolean simulation() {
        return false;
    }

    @Override
    public boolean multiplePaths() {
        return this.multiplePaths;
    }

    @Override
    public KeyCounter usedItems() {
        return TrinityPlanAmounts.copy(this.usedItems);
    }

    @Override
    public KeyCounter emittedItems() {
        return TrinityPlanAmounts.copy(this.emittedItems);
    }

    @Override
    public KeyCounter missingItems() {
        return new KeyCounter();
    }

    /**
     * Trinity execution resolves stable identities on the server thread and never exposes mutable pattern objects.
     */
    @Override
    public Map<IPatternDetails, Long> patternTimes() {
        return Map.of();
    }

    @Override
    public long catalogRevision() {
        return this.catalogRevision;
    }

    @Override
    public CraftingQuantityMode quantityMode() {
        return this.quantityMode;
    }

    @Override
    public Map<AEKey, BigInteger> initialExpectedInputs() {
        return this.initialExpectedInputs;
    }

    @Override
    public Map<TrinityPatternIdentity, BigInteger> patternFirings() {
        return this.patternFirings;
    }

    @Override
    public Map<AEKey, BigInteger> plannedOutputs() {
        return this.plannedOutputs;
    }

    @Override
    public List<TrinityPlanStage> stages() {
        return this.stages;
    }

    @Override
    public List<Integer> stageOrder() {
        return this.stageOrder;
    }

    @Override
    public List<TrinityCycleRepeatBlock> cycleRepeatBlocks() {
        return this.cycleRepeatBlocks;
    }

    @Override
    public Map<AEKey, BigInteger> minimumSeed() {
        return this.minimumSeed;
    }

    @Override
    public Map<AEKey, BigInteger> targetNetChange() {
        return this.targetNetChange;
    }

    @Override
    public List<TrinityPlanningDiagnostic> diagnostics() {
        return this.diagnostics;
    }

    @Override
    public TrinityPlanningStatistics statistics() {
        return this.statistics;
    }

    /**
     * Builder keeps the large plan construction readable while the constructor performs all cross-field validation.
     */
    public static final class Builder {

        private GenericStack finalOutput;
        private long bytes = -1L;
        private boolean multiplePaths;
        private long catalogRevision = -1L;
        private CraftingQuantityMode quantityMode;
        private Map<AEKey, BigInteger> initialExpectedInputs = Map.of();
        private Map<TrinityPatternIdentity, BigInteger> patternFirings = Map.of();
        private List<TrinityPlanStage> stages = List.of();
        private List<Integer> stageOrder = List.of();
        private List<TrinityCycleRepeatBlock> cycleRepeatBlocks = List.of();
        private Map<AEKey, BigInteger> minimumSeed = Map.of();
        private Map<AEKey, BigInteger> targetNetChange = Map.of();
        private Map<AEKey, BigInteger> emittedItems = Map.of();
        private List<TrinityPlanningDiagnostic> diagnostics = List.of();
        private TrinityPlanningStatistics statistics = TrinityPlanningStatistics.empty();

        private Builder() {}

        /**
         * @param value requested target and delivery amount
         * @return this builder
         */
        public Builder finalOutput(GenericStack value) {
            this.finalOutput = value;
            return this;
        }

        /**
         * @param value conservative AE2 CPU capacity charge
         * @return this builder
         */
        public Builder bytes(long value) {
            this.bytes = value;
            return this;
        }

        /**
         * @param value whether the graph offered multiple producer routes
         * @return this builder
         */
        public Builder multiplePaths(boolean value) {
            this.multiplePaths = value;
            return this;
        }

        /**
         * @param value provider revision used by the immutable graph
         * @return this builder
         */
        public Builder catalogRevision(long value) {
            this.catalogRevision = value;
            return this;
        }

        /**
         * @param value requested delivery semantics
         * @return this builder
         */
        public Builder quantityMode(CraftingQuantityMode value) {
            this.quantityMode = value;
            return this;
        }

        /**
         * @param value exact external materials, including seed not produced by a preceding stage
         * @return this builder
         */
        public Builder initialExpectedInputs(Map<AEKey, BigInteger> value) {
            this.initialExpectedInputs = value;
            return this;
        }

        /**
         * @param value aggregate logical firing vector
         * @return this builder
         */
        public Builder patternFirings(Map<TrinityPatternIdentity, BigInteger> value) {
            this.patternFirings = value;
            return this;
        }

        /**
         * @param value compact DAG and cyclic stages
         * @return this builder
         */
        public Builder stages(List<TrinityPlanStage> value) {
            this.stages = value;
            return this;
        }

        /**
         * @param value deterministic topological stage order
         * @return this builder
         */
        public Builder stageOrder(List<Integer> value) {
            this.stageOrder = value;
            return this;
        }

        /**
         * @param value prefix-validated compact repeat blocks
         * @return this builder
         */
        public Builder cycleRepeatBlocks(List<TrinityCycleRepeatBlock> value) {
            this.cycleRepeatBlocks = value;
            return this;
        }

        /**
         * @param value exact maximum prefix deficit
         * @return this builder
         */
        public Builder minimumSeed(Map<AEKey, BigInteger> value) {
            this.minimumSeed = value;
            return this;
        }

        /**
         * @param value exact signed final inventory change
         * @return this builder
         */
        public Builder targetNetChange(Map<AEKey, BigInteger> value) {
            this.targetNetChange = value;
            return this;
        }

        /**
         * @param value items that AE2-compatible execution must emit
         * @return this builder
         */
        public Builder emittedItems(Map<AEKey, BigInteger> value) {
            this.emittedItems = value;
            return this;
        }

        /**
         * @param value informational successful-plan diagnostics
         * @return this builder
         */
        public Builder diagnostics(List<TrinityPlanningDiagnostic> value) {
            this.diagnostics = value;
            return this;
        }

        /**
         * @param value deterministic solver and scheduler counters
         * @return this builder
         */
        public Builder statistics(TrinityPlanningStatistics value) {
            this.statistics = value;
            return this;
        }

        /**
         * @return fully validated immutable plan
         */
        public TrinityCraftingPlanImpl build() {
            if (this.statistics == null) {
                throw new IllegalStateException("Trinity planning statistics are required");
            }
            return new TrinityCraftingPlanImpl(this);
        }
    }
}
