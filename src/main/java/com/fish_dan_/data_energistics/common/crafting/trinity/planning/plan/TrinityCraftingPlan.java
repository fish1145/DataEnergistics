package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan;

import com.fish_dan_.data_energistics.common.crafting.trinity.execution.admission.TrinityCpuExecutablePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.projection.TrinityAe2AmountProjection;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Executable compact plan understood exclusively by Trinity crafting CPUs.
 * <p>
 * Fully validated immutable implementation of the compact Trinity-only crafting plan.
 */
public final class TrinityCraftingPlan implements TrinityCpuExecutablePlan {

    private final GenericStack finalOutput;
    private final BigInteger exactBytes;
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
    private final Map<AEKey, BigInteger> exactEmittedItems;

    private TrinityCraftingPlan(Builder builder) {
        GenericStack finalOutput = builder.finalOutput;
        if (finalOutput == null || finalOutput.amount() <= 0L) {
            throw new IllegalStateException("A Trinity plan requires a positive final output");
        }
        CraftingQuantityMode quantityMode = builder.quantityMode;
        if (builder.exactBytes.signum() < 0 || builder.catalogRevision < 0L || quantityMode == null) {
            throw new IllegalStateException("A Trinity plan requires bytes, revision and quantity mode");
        }

        this.finalOutput = finalOutput;
        this.exactBytes = builder.exactBytes;
        this.multiplePaths = builder.multiplePaths;
        this.catalogRevision = builder.catalogRevision;
        this.quantityMode = quantityMode;
        this.initialExpectedInputs = TrinityPlanAmounts.validatePositive(
                builder.initialExpectedInputs,
                "initial expected input");
        this.patternFirings = validatePatternFirings(builder.patternFirings);
        this.stages = validateStages(builder.stages);
        this.stageOrder = validateStageOrder(builder.stageOrder, this.stages);
        this.cycleRepeatBlocks = validateRepeatBlocks(builder.cycleRepeatBlocks, this.stages);
        this.plannedOutputs = calculatePlannedOutputs(this.stages, this.cycleRepeatBlocks);
        this.minimumSeed = TrinityPlanAmounts.validatePositive(builder.minimumSeed, "minimum seed");
        this.targetNetChange = TrinityPlanAmounts.validateSignedNonZero(builder.targetNetChange, "target net change");
        this.diagnostics = validateDiagnostics(builder.diagnostics);
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

        this.exactEmittedItems = TrinityPlanAmounts.validatePositive(
                builder.emittedItems,
                "emitted item");
    }

    private TrinityCraftingPlan(TrinityCraftingPlan source, TrinityPlanningStatistics statistics) {
        this.finalOutput = source.finalOutput;
        this.exactBytes = source.exactBytes;
        this.multiplePaths = source.multiplePaths;
        this.catalogRevision = source.catalogRevision;
        this.quantityMode = source.quantityMode;
        this.initialExpectedInputs = source.initialExpectedInputs;
        this.patternFirings = source.patternFirings;
        this.plannedOutputs = source.plannedOutputs;
        this.stages = source.stages;
        this.stageOrder = source.stageOrder;
        this.cycleRepeatBlocks = source.cycleRepeatBlocks;
        this.minimumSeed = source.minimumSeed;
        this.targetNetChange = source.targetNetChange;
        this.diagnostics = source.diagnostics;
        this.statistics = statistics;
        this.exactEmittedItems = source.exactEmittedItems;
    }

    /**
     * @return empty large-object builder used by graph planners and persistence migration
     */
    public static Builder builder() {
        return new Builder();
    }

    private static Map<TrinityPatternIdentity, BigInteger> validatePatternFirings(
                                                                                  Map<TrinityPatternIdentity, BigInteger> source) {
        if (source.isEmpty()) {
            throw new IllegalStateException("A Trinity plan requires at least one pattern firing");
        }
        Object2ObjectAVLTreeMap<TrinityPatternIdentity, BigInteger> sorted = new Object2ObjectAVLTreeMap<>();
        source.forEach((identity, count) -> {
            if (count.signum() <= 0) {
                throw new IllegalArgumentException("Trinity aggregate pattern firings must be positive");
            }
            sorted.put(identity, count);
        });
        return Object2ObjectMaps.unmodifiable(sorted);
    }

    private static List<TrinityPlanStage> validateStages(List<TrinityPlanStage> source) {
        if (source.isEmpty()) {
            throw new IllegalStateException("A Trinity plan requires at least one stage");
        }
        IntSet indexes = new IntOpenHashSet();
        for (TrinityPlanStage stage : source) {
            if (!indexes.add(stage.index())) {
                throw new IllegalArgumentException("A Trinity plan requires unique stage indexes");
            }
        }
        for (TrinityPlanStage stage : source) {
            if (!indexes.containsAll(stage.dependencies())) {
                throw new IllegalArgumentException("A Trinity stage dependency is absent from the plan");
            }
        }
        return Collections.unmodifiableList(source);
    }

    private static List<Integer> validateStageOrder(List<Integer> source, List<TrinityPlanStage> stages) {
        if (source.size() != stages.size()) {
            throw new IllegalStateException("A Trinity plan stage order must contain every stage");
        }
        Int2ObjectOpenHashMap<TrinityPlanStage> byIndex = new Int2ObjectOpenHashMap<>();
        stages.forEach(stage -> byIndex.put(stage.index(), stage));
        IntSet completed = new IntOpenHashSet();
        for (int index : source) {
            if (!byIndex.containsKey(index)) {
                throw new IllegalArgumentException("A Trinity plan stage order must be complete and topological");
            }
            TrinityPlanStage stage = byIndex.get(index);
            if (!completed.add(index) || !completed.containsAll(stage.dependencies())) {
                throw new IllegalArgumentException("A Trinity plan stage order must be complete and topological");
            }
        }
        return Collections.unmodifiableList(source);
    }

    private static List<TrinityCycleRepeatBlock> validateRepeatBlocks(
                                                                      List<TrinityCycleRepeatBlock> source,
                                                                      List<TrinityPlanStage> stages) {
        IntSet cycleStages = new IntOpenHashSet();
        stages.stream().filter(TrinityPlanStage::cycleStage).forEach(stage -> cycleStages.add(stage.index()));
        IntSet usedStages = new IntOpenHashSet();
        IntSet blockIndexes = new IntOpenHashSet();
        for (TrinityCycleRepeatBlock block : source) {
            if (!blockIndexes.add(block.index()) ||
                    !cycleStages.containsAll(block.stageOrder())) {
                throw new IllegalArgumentException("A Trinity repeat block must reference unique cycle stages");
            }
            for (int stage : block.stageOrder()) {
                if (!usedStages.add(stage)) {
                    throw new IllegalArgumentException("A Trinity cycle stage cannot belong to multiple repeat blocks");
                }
            }
        }
        if (!usedStages.equals(cycleStages)) {
            throw new IllegalArgumentException("Every Trinity cycle stage must belong to exactly one repeat block");
        }
        return Collections.unmodifiableList(source);
    }

    private static List<TrinityPlanningDiagnostic> validateDiagnostics(List<TrinityPlanningDiagnostic> source) {
        return Collections.unmodifiableList(source);
    }

    private static void validateFiringAggregation(
                                                  Map<TrinityPatternIdentity, BigInteger> expected,
                                                  List<TrinityPlanStage> stages,
                                                  List<TrinityCycleRepeatBlock> repeatBlocks) {
        Int2ObjectOpenHashMap<BigInteger> stageMultipliers = new Int2ObjectOpenHashMap<>();
        stages.forEach(stage -> stageMultipliers.put(stage.index(), BigInteger.ONE));
        for (TrinityCycleRepeatBlock block : repeatBlocks) {
            for (int stage : block.stageOrder()) {
                stageMultipliers.put(stage, block.repetitions());
            }
        }
        Object2ObjectAVLTreeMap<TrinityPatternIdentity, BigInteger> actual = new Object2ObjectAVLTreeMap<>();
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
        Int2ObjectOpenHashMap<BigInteger> stageMultipliers = new Int2ObjectOpenHashMap<>();
        stages.forEach(stage -> stageMultipliers.put(stage.index(), BigInteger.ONE));
        for (TrinityCycleRepeatBlock block : repeatBlocks) {
            for (int stage : block.stageOrder()) {
                stageMultipliers.put(stage, block.repetitions());
            }
        }

        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> outputs = new Object2ObjectLinkedOpenHashMap<>();
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
        return TrinityPlanAmounts.validatePositive(outputs, "planned output");
    }

    private static void validateNetChange(
                                          Map<AEKey, BigInteger> expected,
                                          List<TrinityPlanStage> stages,
                                          List<TrinityCycleRepeatBlock> repeatBlocks) {
        Int2ObjectOpenHashMap<TrinityPlanStage> stagesByIndex = new Int2ObjectOpenHashMap<>();
        stages.forEach(stage -> stagesByIndex.put(stage.index(), stage));
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> actual = new Object2ObjectLinkedOpenHashMap<>();
        stages.stream()
                .filter(stage -> !stage.cycleStage())
                .forEach(stage -> mergeNet(actual, stage.netChange(), BigInteger.ONE));
        for (TrinityCycleRepeatBlock block : repeatBlocks) {
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> expectedBlock = new Object2ObjectLinkedOpenHashMap<>();
            for (int stageIndex : block.stageOrder()) {
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
        Int2ObjectOpenHashMap<TrinityPlanStage> stagesByIndex = new Int2ObjectOpenHashMap<>();
        stages.forEach(stage -> stagesByIndex.put(stage.index(), stage));
        Int2ObjectOpenHashMap<TrinityCycleRepeatBlock> blockByStage = new Int2ObjectOpenHashMap<>();
        for (TrinityCycleRepeatBlock block : repeatBlocks) {
            for (int stage : block.stageOrder()) {
                blockByStage.put(stage, block);
            }
        }
        IntSet completedBlocks = new IntOpenHashSet();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> balances = new Object2ObjectLinkedOpenHashMap<>(
                initialInputs);
        for (int stageIndex : stageOrder) {
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
        return TrinityAe2AmountProjection.toAe2Bytes(this.exactBytes);
    }

    /** Returns exact CPU storage accounting before the AE2 long-only projection. */
    public BigInteger exactBytes() {
        return this.exactBytes;
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
        return TrinityAe2AmountProjection.toKeyCounter(this.initialExpectedInputs);
    }

    @Override
    public KeyCounter emittedItems() {
        return TrinityAe2AmountProjection.toKeyCounter(this.exactEmittedItems);
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

    /**
     * @return crafting-provider revision from which every retained pattern signature was captured
     */
    public long catalogRevision() {
        return this.catalogRevision;
    }

    /**
     * @return requested net-new or final-total delivery semantics
     */
    public CraftingQuantityMode quantityMode() {
        return this.quantityMode;
    }

    /**
     * @return exact external initial materials, including seed that no preceding stage can produce
     */
    public Map<AEKey, BigInteger> initialExpectedInputs() {
        return this.initialExpectedInputs;
    }

    /**
     * @return aggregate logical firing count keyed by stable published pattern identity
     */
    public Map<TrinityPatternIdentity, BigInteger> patternFirings() {
        return this.patternFirings;
    }

    /**
     * @return exact aggregate pattern-declared outputs used by confirmation and CPU status projections
     */
    public Map<AEKey, BigInteger> plannedOutputs() {
        return this.plannedOutputs;
    }

    /**
     * @return immutable dependency-addressable stages
     */
    public List<TrinityPlanStage> stages() {
        return this.stages;
    }

    /**
     * @return deterministic topological stage order
     */
    public List<Integer> stageOrder() {
        return this.stageOrder;
    }

    /**
     * @return compact cyclic repeat blocks
     */
    public List<TrinityCycleRepeatBlock> cycleRepeatBlocks() {
        return this.cycleRepeatBlocks;
    }

    /**
     * @return exact maximum prefix deficit reserved before execution
     */
    public Map<AEKey, BigInteger> minimumSeed() {
        return this.minimumSeed;
    }

    /**
     * @return informational diagnostics retained with a successful plan
     */
    public List<TrinityPlanningDiagnostic> diagnostics() {
        return this.diagnostics;
    }

    /**
     * @return deterministic solver and scheduler counters
     */
    public TrinityPlanningStatistics statistics() {
        return this.statistics;
    }

    /**
     * Creates a request-local view with fresh metrics while sharing this plan's validated immutable execution data.
     *
     * @param value metrics measured for the current planning request
     * @return independent plan view that leaves a cached plan untouched
     */
    public TrinityCraftingPlan withPlanningStatistics(TrinityPlanningStatistics value) {
        return new TrinityCraftingPlan(this, value);
    }

    /**
     * Builder keeps the large plan construction readable while the constructor performs all cross-field validation.
     */
    public static final class Builder {

        private @Nullable GenericStack finalOutput;
        private BigInteger exactBytes = BigInteger.valueOf(-1L);
        private boolean multiplePaths;
        private long catalogRevision = -1L;
        private @Nullable CraftingQuantityMode quantityMode;
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
        public Builder bytes(BigInteger value) {
            this.exactBytes = value;
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
        public TrinityCraftingPlan build() {
            return new TrinityCraftingPlan(this);
        }
    }
}
