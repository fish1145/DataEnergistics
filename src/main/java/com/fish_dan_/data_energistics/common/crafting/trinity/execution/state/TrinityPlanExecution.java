package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityExecutionNbtCodec;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityExecutionSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityExecutionSnapshot.Firing;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityExecutionSnapshot.RepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityExecutionSnapshot.Stage;
import com.fish_dan_.data_energistics.common.crafting.trinity.execution.state.persistence.TrinityExecutionSnapshot.WaitKind;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCycleRepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * Event-driven, persistence-safe execution cursor for a compact Trinity crafting plan.
 * <p>
 * Deterministic execution implementation with transient event indexes and versioned persistence.
 */
public final class TrinityPlanExecution {

    /**
     * Observable execution states used by the CPU scheduler and status UI.
     */
    public enum Status {
        /**
         * At least one stage is eligible to dispatch.
         */
        READY,
        /**
         * An eligible stage waits for a known material change.
         */
        WAITING_INPUT,
        /**
         * A cyclic stage waits for a dynamic variant or material retry.
         */
        WAITING_DYNAMIC_INPUT,
        /**
         * A stage waits for a provider retry.
         */
        WAITING_PROVIDER,
        /**
         * Remaining work is being replanned against a new catalog revision.
         */
        PLANNING,
        /**
         * The current physical dispatch budget ended and resumes on a later tick.
         */
        BUDGET_EXHAUSTED,
        /**
         * Every planned logical firing has completed.
         */
        COMPLETED,
        /**
         * Execution terminated with a structured failure reason.
         */
        FAILED
    }

    /**
     * Immutable dispatch offer for exactly one current stage firing.
     *
     * @param generation            execution generation used to reject stale work after replanning
     * @param stageIndex            stable plan stage index
     * @param firingIndex           ordered firing index inside the stage
     * @param patternIdentity       stable published pattern signature
     * @param primaryOutput         key used for live provider-pattern resolution
     * @param plannedVariantOrdinal variant selected by the plan
     * @param maximumLogicalFirings maximum count this dispatch may accept
     * @param cycle                 whether the firing belongs to a repeat block
     */
    public record Work(long generation,
                       int stageIndex,
                       int firingIndex,
                       TrinityPatternIdentity patternIdentity,
                       AEKey primaryOutput,
                       int plannedVariantOrdinal,
                       long maximumLogicalFirings,
                       boolean cycle) {

        /**
         * Rejects incomplete or non-dispatchable offers before provider code receives them.
         */
        public Work {
            if (generation < 0L || stageIndex < 0 || firingIndex < 0 ||
                    patternIdentity == null || primaryOutput == null || plannedVariantOrdinal < 0 ||
                    maximumLogicalFirings <= 0L) {
                throw new IllegalArgumentException("A Trinity execution work offer must be complete and positive");
            }
        }
    }

    /**
     * Immutable seed-aware limit for the current cycle work offer.
     *
     * @param maximumLogicalFirings safe provider offer count; zero means the wave cannot start yet
     * @param observedKeys          repeat minimum-seed keys whose changes may make the wave startable
     */
    public record CycleWaveLimit(long maximumLogicalFirings, Set<AEKey> observedKeys) {

        /**
         * Validates the non-negative limit and isolates the wake-key set from callers.
         */
        public CycleWaveLimit {
            if (maximumLogicalFirings < 0L || observedKeys == null) {
                throw new IllegalArgumentException("A Trinity cycle wave limit requires a non-negative count and key set");
            }
            observedKeys = Set.copyOf(observedKeys);
        }
    }

    private final AEKey targetKey;
    private final long targetAmount;
    private final LinkedHashMap<Integer, StageState> stages = new LinkedHashMap<>();
    private final ArrayList<Integer> stageOrder = new ArrayList<>();
    private final LinkedHashMap<Integer, RepeatState> repeatBlocks = new LinkedHashMap<>();
    private final HashMap<Integer, RepeatState> repeatByStage = new HashMap<>();
    private final LinkedHashMap<AEKey, Long> seedReserve = new LinkedHashMap<>();
    private final TrinityBorrowingLedger borrowingLedger;

    private final ArrayDeque<Integer> readyQueue = new ArrayDeque<>();
    private final HashSet<Integer> queuedStages = new HashSet<>();
    private final HashMap<AEKey, LinkedHashSet<Integer>> inputStageIndex = new HashMap<>();
    private final HashMap<Integer, LinkedHashSet<Integer>> dependents = new HashMap<>();
    private final PriorityQueue<RetryEntry> retryQueue = new PriorityQueue<>(Comparator
            .comparingLong(RetryEntry::retryAt)
            .thenComparingInt(RetryEntry::stageIndex)
            .thenComparingLong(RetryEntry::version));

    private long catalogRevision;
    private CraftingQuantityMode quantityMode;
    private long generation;
    private long durableRevision;
    private Work currentWork;
    private boolean planning;
    private boolean failed;
    private String failureReason = "";
    private long budgetRetryAt = -1L;
    private boolean completionSealed;
    private long completionBuffer;
    private long deliveryRemaining;
    private final LinkedHashMap<AEKey, Long> actualFinalOutputs = new LinkedHashMap<>();

    private TrinityPlanExecution(AEKey targetKey,
                                 long targetAmount,
                                 TrinityBorrowingLedger borrowingLedger) {
        if (targetAmount <= 0L) {
            throw new IllegalArgumentException("A Trinity execution requires a positive target amount");
        }
        this.targetKey = targetKey;
        this.targetAmount = targetAmount;
        this.deliveryRemaining = targetAmount;
        this.borrowingLedger = borrowingLedger;
    }

    /**
     * Creates a fresh execution cursor from a fully validated compact plan.
     *
     * @param plan        executable Trinity plan
     * @param currentTick current server tick
     * @return initialized execution state
     */
    public static TrinityPlanExecution create(TrinityCraftingPlan plan, long currentTick) {
        if (plan == null || currentTick < 0L) {
            throw new IllegalArgumentException("A Trinity execution requires a plan and non-negative server tick");
        }
        GenericStack output = plan.finalOutput();
        TrinityPlanExecution execution = new TrinityPlanExecution(
                output.what(),
                output.amount(),
                new TrinityBorrowingLedger());
        execution.installPlan(plan);
        execution.rebuildTransientState(currentTick);
        return execution;
    }

    /**
     * Restores a supported compact execution snapshot and deterministically rebuilds its transient queues and indexes.
     *
     * @param tag         encoded execution state
     * @param registries  server registry lookup used by AE key codecs
     * @param currentTick current server tick used to release due retries
     * @return restored execution state
     */
    public static TrinityPlanExecution restore(CompoundTag tag,
                                               HolderLookup.Provider registries,
                                               long currentTick) {
        requireTick(currentTick);
        TrinityExecutionSnapshot snapshot = TrinityExecutionNbtCodec.decode(tag, registries);
        TrinityPlanExecution restored = new TrinityPlanExecution(
                snapshot.targetKey(),
                snapshot.targetAmount(),
                new TrinityBorrowingLedger(snapshot.borrowingEntries()));
        restored.catalogRevision = snapshot.catalogRevision();
        restored.quantityMode = snapshot.quantityMode();
        restored.generation = snapshot.generation();
        restored.failureReason = snapshot.failureReason();
        restored.completionSealed = snapshot.completionSealed();
        restored.completionBuffer = snapshot.completionBuffer();
        restored.deliveryRemaining = snapshot.deliveryRemaining();
        restored.actualFinalOutputs.putAll(snapshot.actualFinalOutputs());
        restored.budgetRetryAt = rebaseRetryAt(
                snapshot.budgetRetryAt(),
                snapshot.savedAtTick(),
                currentTick);
        restored.stageOrder.addAll(snapshot.stageOrder());
        for (Stage stageSnapshot : snapshot.stages()) {
            StageState stage = StageState.fromSnapshot(
                    stageSnapshot,
                    snapshot.savedAtTick(),
                    currentTick);
            if (restored.stages.putIfAbsent(stage.index, stage) != null) {
                throw new IllegalArgumentException("A Trinity execution contains duplicate stage indexes");
            }
        }
        for (RepeatBlock repeatSnapshot : snapshot.repeatBlocks()) {
            RepeatState repeat = RepeatState.fromSnapshot(repeatSnapshot, restored.stages);
            if (restored.repeatBlocks.putIfAbsent(repeat.index, repeat) != null) {
                throw new IllegalArgumentException("A Trinity execution contains duplicate repeat indexes");
            }
            for (Integer stageIndex : repeat.stageOrder) {
                if (restored.repeatByStage.putIfAbsent(stageIndex, repeat) != null) {
                    throw new IllegalArgumentException("A Trinity cycle stage belongs to multiple restored repeats");
                }
            }
        }
        restored.seedReserve.putAll(snapshot.seedReserve());
        boolean missingOutputProjection = restored.stages.values().stream()
                .flatMap(stage -> stage.firings.stream())
                .anyMatch(firing -> firing.outputs.isEmpty());
        if (missingOutputProjection) {
            Data_Energistics.LOGGER.warn(
                    "Restored legacy Trinity execution without exact pending-output metadata; " +
                            "CPU status omits unknown pending rows until replanning or completion: target={}, revision={}",
                    restored.targetKey,
                    restored.catalogRevision);
        }
        restored.validateInstalledPlan();
        restored.validateRestoredCursors();
        restored.validateCompletionState();

        Status persistedStatus = snapshot.status();
        restored.failed = persistedStatus == Status.FAILED;
        restored.planning = persistedStatus == Status.PLANNING;
        if (restored.failed == restored.failureReason.isBlank()) {
            throw new IllegalArgumentException("A Trinity failure reason must exist exactly for failed execution");
        }
        if ((persistedStatus == Status.BUDGET_EXHAUSTED) != (restored.budgetRetryAt >= 0L)) {
            throw new IllegalArgumentException("A Trinity budget state must retain exactly one retry tick");
        }
        if (persistedStatus != Status.BUDGET_EXHAUSTED && restored.budgetRetryAt != -1L) {
            throw new IllegalArgumentException("A Trinity non-budget state cannot retain a budget retry tick");
        }
        if ((persistedStatus == Status.COMPLETED) != restored.productionComplete()) {
            throw new IllegalArgumentException("A Trinity completed status must match all persisted stage cursors");
        }
        restored.validatePersistedStatusShape(persistedStatus);

        restored.rebuildTransientState(currentTick);
        return restored;
    }

    /**
     * @return current scheduler-visible state
     */
    public Status status() {
        if (this.failed) {
            return Status.FAILED;
        }
        if (this.planning) {
            return Status.PLANNING;
        }
        if (productionComplete()) {
            return Status.COMPLETED;
        }
        if (this.budgetRetryAt >= 0L) {
            return Status.BUDGET_EXHAUSTED;
        }
        if (this.currentWork != null || !this.readyQueue.isEmpty()) {
            return Status.READY;
        }
        boolean waitingInput = false;
        boolean waitingProvider = false;
        for (StageState stage : this.stages.values()) {
            if (stage.waitKind == WaitKind.DYNAMIC_INPUT) {
                return Status.WAITING_DYNAMIC_INPUT;
            }
            waitingProvider |= stage.waitKind == WaitKind.PROVIDER;
            waitingInput |= stage.waitKind == WaitKind.INPUT;
        }
        if (waitingProvider) {
            return Status.WAITING_PROVIDER;
        }
        if (waitingInput) {
            return Status.WAITING_INPUT;
        }
        return Status.READY;
    }

    /**
     * Returns the earliest deterministic provider, dynamic-input or budget retry tick.
     *
     * @return retry tick, or empty when the current status is not time-gated
     */
    public OptionalLong nextRetryTick() {
        long nextRetry = this.budgetRetryAt;
        for (StageState stage : this.stages.values()) {
            if (stage.waitKind.retrying() && stage.retryAt >= 0L &&
                    (nextRetry < 0L || stage.retryAt < nextRetry)) {
                nextRetry = stage.retryAt;
            }
        }
        return nextRetry < 0L ? OptionalLong.empty() : OptionalLong.of(nextRetry);
    }

    /**
     * @return catalog revision against which current remaining stages were planned
     */
    public long catalogRevision() {
        return this.catalogRevision;
    }

    /**
     * @return requested delivery quantity semantics
     */
    public CraftingQuantityMode quantityMode() {
        return this.quantityMode;
    }

    /**
     * @return immutable requested delivery stack
     */
    public GenericStack finalOutput() {
        return new GenericStack(this.targetKey, this.targetAmount);
    }

    /**
     * Returns the current leased work, or dequeues one eligible stage without scanning unrelated stages.
     *
     * @param currentTick current server tick used to activate due retries
     * @return current dispatch offer, if any
     */
    public Optional<Work> poll(long currentTick) {
        requireTick(currentTick);
        if (this.failed || this.planning || productionComplete()) {
            return Optional.empty();
        }
        activateDueRetries(currentTick);
        if (this.budgetRetryAt >= 0L && currentTick >= this.budgetRetryAt) {
            this.budgetRetryAt = -1L;
            markDurableMutation();
        }
        if (this.currentWork != null) {
            return Optional.of(this.currentWork);
        }
        if (this.budgetRetryAt >= 0L) {
            return Optional.empty();
        }
        while (!this.readyQueue.isEmpty()) {
            int stageIndex = this.readyQueue.removeFirst();
            this.queuedStages.remove(stageIndex);
            StageState stage = requireStage(stageIndex);
            if (!eligible(stage)) {
                continue;
            }
            this.currentWork = createWork(stage);
            stage.leased = true;
            return Optional.of(this.currentWork);
        }
        return Optional.empty();
    }

    /**
     * @return number of unique stage indexes currently held in the transient ready queue
     */
    public int queuedStageCount() {
        return this.queuedStages.size();
    }

    /**
     * Registers material keys whose changes can wake one stage; registrations are persisted, while the reverse index is
     * not.
     *
     * @param stageIndex stable stage index
     * @param keys       legal material keys relevant to that stage
     */
    public void registerInputKeys(int stageIndex, Set<AEKey> keys) {
        StageState stage = requireStage(stageIndex);
        Set<AEKey> copied = copyKeys(keys, "registered input");
        boolean changed = false;
        for (AEKey key : copied) {
            if (stage.inputKeys.add(key)) {
                this.inputStageIndex.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(stageIndex);
                changed = true;
            }
        }
        if (changed) {
            markDurableMutation();
        }
    }

    /**
     * Wakes only stages indexed by the changed material key.
     *
     * @param key changed storage key
     * @return whether at least one waiting stage was released
     */
    public boolean wake(AEKey key) {
        if (key == null) {
            throw new IllegalArgumentException("A Trinity wake event requires an AE key");
        }
        Set<Integer> indexed = this.inputStageIndex.get(key);
        if (indexed == null || indexed.isEmpty()) {
            return false;
        }
        boolean released = false;
        for (Integer stageIndex : List.copyOf(indexed)) {
            StageState stage = requireStage(stageIndex);
            if ((stage.waitKind == WaitKind.INPUT || stage.waitKind == WaitKind.DYNAMIC_INPUT) &&
                    stage.waitingKeys.contains(key)) {
                clearWait(stage);
                enqueueIfEligible(stage);
                released = true;
            }
        }
        if (released) {
            markDurableMutation();
        }
        return released;
    }

    /**
     * Defers current work until one of its required material keys changes.
     *
     * @param work current leased work
     * @param keys material keys that can satisfy the wait
     */
    public void deferInput(Work work, Set<AEKey> keys) {
        Set<AEKey> copied = copyNonEmptyKeys(keys, "input wait");
        StageState stage = releaseCurrentWork(work);
        registerInputKeys(stage.index, copied);
        beginWait(stage, WaitKind.INPUT, copied, -1L);
        markDurableMutation();
    }

    /**
     * Defers current cyclic work with event wakeup plus exponential tick backoff.
     *
     * @param work          current leased work
     * @param keys          keys that may make a dynamic route feasible
     * @param currentTick   current server tick
     * @param maxRetryTicks inclusive retry delay cap
     */
    public void deferDynamicInput(Work work, Set<AEKey> keys, long currentTick, int maxRetryTicks) {
        requireTick(currentTick);
        requireRetryCap(maxRetryTicks);
        Set<AEKey> copied = copyNonEmptyKeys(keys, "dynamic input wait");
        StageState stage = requireCurrentWork(work);
        if (!stage.cycle) {
            throw new IllegalStateException("Only a Trinity cycle stage may wait for dynamic material selection");
        }
        clearCurrentWork(stage);
        registerInputKeys(stage.index, copied);
        int delay = stage.nextDynamicDelay;
        beginWait(stage, WaitKind.DYNAMIC_INPUT, copied, Math.addExact(currentTick, delay));
        stage.nextDynamicDelay = nextDelay(delay, maxRetryTicks);
        scheduleRetry(stage);
        markDurableMutation();
    }

    /**
     * Defers current work with provider-specific exponential tick backoff.
     *
     * @param work          current leased work
     * @param currentTick   current server tick
     * @param maxRetryTicks inclusive retry delay cap
     */
    public void deferProvider(Work work, long currentTick, int maxRetryTicks) {
        requireTick(currentTick);
        requireRetryCap(maxRetryTicks);
        StageState stage = releaseCurrentWork(work);
        int delay = stage.nextProviderDelay;
        beginWait(stage, WaitKind.PROVIDER, Set.of(), Math.addExact(currentTick, delay));
        stage.nextProviderDelay = nextDelay(delay, maxRetryTicks);
        scheduleRetry(stage);
        markDurableMutation();
    }

    /**
     * Suspends dispatch while the caller replans the complete remaining request.
     *
     * @param work current leased work whose pattern or route became invalid
     */
    public void markPlanning(Work work) {
        releaseCurrentWork(work);
        this.planning = true;
        this.budgetRetryAt = -1L;
        markDurableMutation();
    }

    /**
     * Replaces all remaining stage cursors after a successful replan while retaining ledger and completion ownership.
     *
     * @param replacement replacement plan for the remaining request
     * @param currentTick current server tick
     */
    public void replaceRemainingPlan(TrinityCraftingPlan replacement, long currentTick) {
        requireTick(currentTick);
        if (!this.planning || replacement == null) {
            throw new IllegalStateException("A Trinity replacement plan is accepted only while planning");
        }
        GenericStack output = replacement.finalOutput();
        if (!this.targetKey.equals(output.what()) || replacement.quantityMode() != this.quantityMode ||
                output.amount() > this.deliveryRemaining) {
            throw new IllegalArgumentException("A Trinity replacement plan must preserve target and quantity semantics");
        }
        long nextGeneration = Math.addExact(this.generation, 1L);
        TrinityPlanExecution prepared = new TrinityPlanExecution(
                this.targetKey,
                this.targetAmount,
                this.borrowingLedger);
        prepared.installPlan(replacement);
        adoptInstalledPlan(prepared);
        this.generation = nextGeneration;
        this.planning = false;
        this.budgetRetryAt = -1L;
        rebuildTransientState(currentTick);
        markDurableMutation();
    }

    /**
     * Records the exact count accepted by a provider and advances only that firing cursor.
     *
     * <p>For an unstarted cycle wave, {@code offeredLogicalFirings} is the seed-safe logical offer calculated before
     * provider capacity slicing. It must not be replaced with {@code acceptedCount}: a provider may accept only a
     * partial physical slice, but the established wave still has to retain its original logical size.</p>
     *
     * @param work                    current leased work
     * @param acceptedCount           positive accepted logical firing count
     * @param offeredLogicalFirings   logical offer that established an unstarted cycle wave
     */
    public void recordAccepted(Work work, long acceptedCount, long offeredLogicalFirings) {
        if (acceptedCount <= 0L) {
            throw new IllegalArgumentException("A Trinity provider acceptance must be positive");
        }
        if (offeredLogicalFirings <= 0L) {
            throw new IllegalArgumentException("A Trinity provider logical offer must be positive");
        }
        StageState stage = requireCurrentWork(work);
        if (offeredLogicalFirings > work.maximumLogicalFirings()) {
            throw new IllegalArgumentException("A Trinity logical offer exceeds the leased work bound");
        }
        if (acceptedCount > offeredLogicalFirings) {
            throw new IllegalArgumentException("A Trinity provider accepted more than the offered logical firing count");
        }
        FiringState firing = stage.firings.get(stage.currentFiring);
        RepeatState repeat = stage.cycle ? requireRepeat(stage.index) : null;
        if (stage.cycle && repeat.waveCount == 0L) {
            if (repeat.cursor != 0 || stage.currentFiring != 0) {
                throw new IllegalStateException("A Trinity cycle wave must begin at its first stage and firing");
            }
            repeat.waveCount = Math.min(
                    repeat.remainingRepetitions,
                    ceilDiv(offeredLogicalFirings, firing.plannedCount));
            if (repeat.waveCount <= 0L) {
                throw new IllegalStateException("A Trinity cycle wave offer cannot establish an empty wave");
            }
            firing.remainingCount = Math.multiplyExact(firing.plannedCount, repeat.waveCount);
            firing.initialized = true;
        }
        if (!firing.initialized || firing.remainingCount < acceptedCount) {
            throw new IllegalStateException("A Trinity firing cursor cannot consume beyond its initialized wave");
        }
        firing.remainingCount -= acceptedCount;
        clearCurrentWork(stage);
        stage.nextDynamicDelay = 1;
        stage.nextProviderDelay = 1;
        markDurableMutation();

        if (firing.remainingCount > 0L) {
            enqueueIfEligible(stage);
            return;
        }
        stage.currentFiring++;
        if (stage.currentFiring < stage.firings.size()) {
            initializeCurrentFiring(stage);
            enqueueIfEligible(stage);
            return;
        }
        finishStage(stage);
    }

    /**
     * Releases current work and defers it until the next server tick.
     *
     * @param work        current leased work
     * @param currentTick current server tick at budget exhaustion
     */
    public void markBudgetExhausted(Work work, long currentTick) {
        requireTick(currentTick);
        StageState stage = releaseCurrentWork(work);
        enqueueIfEligible(stage);
        this.budgetRetryAt = Math.addExact(currentTick, 1L);
        markDurableMutation();
    }

    /**
     * Determines the strict terminal deadlock condition without mutating or refunding state.
     *
     * @param hasInFlight whether a provider still owns unfinished output
     * @return true only when remaining work has no ready, retry, wait, planning, budget or in-flight path
     */
    public boolean deadlocked(boolean hasInFlight) {
        if (hasInFlight || this.failed || this.planning || productionComplete() || this.currentWork != null ||
                !this.readyQueue.isEmpty() || this.budgetRetryAt >= 0L) {
            return false;
        }
        for (StageState stage : this.stages.values()) {
            if (stage.waitKind != WaitKind.NONE) {
                return false;
            }
        }
        return true;
    }

    /**
     * Terminates remaining execution with a non-empty diagnostic reason.
     *
     * @param reason structured failure reason suitable for logging and persistence
     */
    public void fail(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A Trinity execution failure requires a diagnostic reason");
        }
        if (productionComplete()) {
            throw new IllegalStateException("A completed Trinity execution cannot fail");
        }
        this.failed = true;
        this.failureReason = reason;
        this.planning = false;
        this.budgetRetryAt = -1L;
        this.currentWork = null;
        this.readyQueue.clear();
        this.queuedStages.clear();
        this.retryQueue.clear();
        this.stages.values().forEach(stage -> {
            stage.leased = false;
            clearWait(stage);
        });
        markDurableMutation();
    }

    /**
     * @return failure reason when status is {@link Status#FAILED}
     */
    public Optional<String> failureReason() {
        return this.failed ? Optional.of(this.failureReason) : Optional.empty();
    }

    /**
     * @return immutable exact seed reserve captured from the current plan
     */
    public Map<AEKey, Long> seedReserve() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.seedReserve));
    }

    /**
     * Calculates the largest logical firing count that may start the current cycle wave without consuming the
     * repeat block's required seed before the rest of that wave has been accounted for.
     *
     * <p>
     * This is a pure query over the caller's combined CPU/network availability. It returns zero when the first stage
     * of an unstarted wave lacks its minimum seed. Once a provider has accepted part of a wave, the established wave
     * is returned unchanged so later stages and partial dispatches cannot be re-truncated by changing availability.
     * </p>
     *
     * @param work            current leased cycle work
     * @param availableAmount non-negative currently available amount for one key
     * @return safe maximum and the seed keys that can wake an unstartable wave
     */
    public CycleWaveLimit maximumCycleLogicalFirings(Work work, ToLongFunction<AEKey> availableAmount) {
        if (availableAmount == null) {
            throw new IllegalArgumentException("A Trinity cycle seed query requires material availability");
        }
        StageState stage = requireCurrentWork(work);
        if (!stage.cycle) {
            throw new IllegalArgumentException("Only Trinity cycle work has a seed-limited wave");
        }
        RepeatState repeat = requireRepeat(stage.index);
        if (repeat.waveCount > 0L) {
            return new CycleWaveLimit(work.maximumLogicalFirings(), repeat.minimumSeed.keySet());
        }
        if (repeat.cursor != 0 || repeat.stageOrder.getFirst() != stage.index || stage.currentFiring != 0) {
            throw new IllegalStateException("An unstarted Trinity cycle wave must begin at its first stage and firing");
        }

        FiringState firing = stage.firings.get(stage.currentFiring);
        long repetitions = repeat.remainingRepetitions;
        for (Map.Entry<AEKey, Long> seed : repeat.minimumSeed.entrySet()) {
            long available = availableAmount.applyAsLong(seed.getKey());
            if (available < 0L) {
                throw new IllegalArgumentException("A Trinity cycle seed availability cannot be negative");
            }
            repetitions = Math.min(repetitions, available / seed.getValue());
        }
        return new CycleWaveLimit(
                Math.multiplyExact(firing.plannedCount, repetitions),
                repeat.minimumSeed.keySet());
    }

    /**
     * Reconstructs the exact pattern-declared outputs of every undispatched firing without expanding cycle repeats.
     * Legacy schema 2/3 firings that predate output metadata are omitted instead of reporting invented values.
     *
     * @return immutable pending-output projection for AE2's crafting CPU status table
     */
    public Map<AEKey, Long> pendingOutputs() {
        LinkedHashMap<AEKey, Long> outputs = new LinkedHashMap<>();
        for (StageState stage : this.stages.values()) {
            if (!stage.cycle) {
                addDagPendingOutputs(outputs, stage);
            }
        }
        for (RepeatState repeat : this.repeatBlocks.values()) {
            addCyclePendingOutputs(outputs, repeat);
        }
        return Collections.unmodifiableMap(outputs);
    }

    /**
     * Reports whether every retained firing has the output metadata required to reconstruct a complete pending
     * projection. Legacy schema 2/3 snapshots may not have this metadata.
     *
     * @return true when {@link #pendingOutputs()} is complete
     */
    public boolean hasExactPendingOutputProjection() {
        return this.stages.values().stream()
                .flatMap(stage -> stage.firings.stream())
                .noneMatch(firing -> firing.outputs.isEmpty());
    }

    private static void addDagPendingOutputs(Map<AEKey, Long> outputs, StageState stage) {
        if (stage.completed) {
            return;
        }
        for (int index = stage.currentFiring; index < stage.firings.size(); index++) {
            FiringState firing = stage.firings.get(index);
            long remaining = index == stage.currentFiring && firing.initialized ?
                    firing.remainingCount :
                    firing.plannedCount;
            mergePendingOutputs(outputs, firing, remaining);
        }
    }

    private void addCyclePendingOutputs(Map<AEKey, Long> outputs, RepeatState repeat) {
        if (repeat.remainingRepetitions == 0L) {
            return;
        }
        for (int stagePosition = 0; stagePosition < repeat.stageOrder.size(); stagePosition++) {
            StageState stage = requireStage(repeat.stageOrder.get(stagePosition));
            for (int firingIndex = 0; firingIndex < stage.firings.size(); firingIndex++) {
                FiringState firing = stage.firings.get(firingIndex);
                long remaining = cycleFiringRemainder(repeat, stage, stagePosition, firing, firingIndex);
                mergePendingOutputs(outputs, firing, remaining);
            }
        }
    }

    private static long cycleFiringRemainder(RepeatState repeat,
                                             StageState stage,
                                             int stagePosition,
                                             FiringState firing,
                                             int firingIndex) {
        if (repeat.waveCount == 0L || stagePosition > repeat.cursor ||
                (stagePosition == repeat.cursor && firingIndex > stage.currentFiring)) {
            return Math.multiplyExact(firing.plannedCount, repeat.remainingRepetitions);
        }
        long laterWaves = Math.subtractExact(repeat.remainingRepetitions, repeat.waveCount);
        long laterCount = Math.multiplyExact(firing.plannedCount, laterWaves);
        if (stagePosition < repeat.cursor || firingIndex < stage.currentFiring) {
            return laterCount;
        }
        long activeCount = firing.initialized ?
                firing.remainingCount :
                Math.multiplyExact(firing.plannedCount, repeat.waveCount);
        return Math.addExact(activeCount, laterCount);
    }

    private static void mergePendingOutputs(Map<AEKey, Long> outputs,
                                            FiringState firing,
                                            long firingCount) {
        if (firingCount == 0L || firing.outputs.isEmpty()) {
            return;
        }
        firing.outputs.forEach((key, perFiring) -> outputs.merge(
                key,
                Math.multiplyExact(perFiring, firingCount),
                Math::addExact));
    }

    /**
     * @return ownership ledger shared by dynamic material transactions
     */
    public TrinityBorrowingLedger borrowingLedger() {
        return this.borrowingLedger;
    }

    /**
     * @return whether every planned stage and repeat block has finished
     */
    public boolean productionComplete() {
        for (StageState stage : this.stages.values()) {
            if (!stage.completed) {
                return false;
            }
        }
        return true;
    }

    /**
     * Isolates the exact target delivery amount from the working inventory after production and in-flight completion.
     *
     * @param amount exact amount moved into the completion buffer
     */
    public void sealCompletion(long amount) {
        long actualAmount = actualFinalOutputAmount();
        if (!productionComplete() || this.failed || this.completionSealed || amount < 0L ||
                Math.addExact(amount, actualAmount) != this.deliveryRemaining) {
            throw new IllegalStateException("A Trinity completion buffer requires one exact post-production delivery seal");
        }
        this.completionSealed = true;
        this.completionBuffer = Math.addExact(amount, actualAmount);
        markDurableMutation();
    }

    /**
     * Retains an actual same-item final output outside the working inventory so exact downstream inputs cannot use it.
     *
     * @param actualKey actual machine-returned key with all Data Components intact
     * @param amount    positive accepted amount
     */
    public void recordActualFinalOutput(AEItemKey actualKey, long amount) {
        if (this.completionSealed || amount <= 0L || !(this.targetKey instanceof AEItemKey targetItem) ||
                actualKey.getItem() != targetItem.getItem()) {
            throw new IllegalArgumentException("An actual Trinity final output must match the requested registered item");
        }
        long actualAmount = actualFinalOutputAmount();
        if (amount > this.deliveryRemaining - actualAmount) {
            throw new IllegalArgumentException("Actual Trinity final outputs exceed the undelivered request");
        }
        this.actualFinalOutputs.merge(actualKey, amount, Math::addExact);
        markDurableMutation();
    }

    /**
     * @return total actual variants already owned for final delivery
     */
    public long actualFinalOutputAmount() {
        return this.actualFinalOutputs.values().stream().mapToLong(Long::longValue).reduce(0L, Math::addExact);
    }

    /**
     * Marks the exact final target as completed without creating a completion buffer or delivering an item.
     *
     * <p>
     * This is reserved for declared control-token outputs such as marked order packages. Production must already be
     * complete so the operation remains persistence-compatible with the ordinary sealed-completion state.
     * </p>
     *
     * @param amount exact remaining target amount completed virtually
     */
    public void completeVirtually(long amount) {
        if (!productionComplete() || this.failed || this.completionSealed ||
                !this.actualFinalOutputs.isEmpty() || amount != this.deliveryRemaining) {
            throw new IllegalStateException(
                    "A Trinity virtual completion requires the exact remaining target after production completes");
        }
        this.completionSealed = true;
        this.completionBuffer = 0L;
        this.deliveryRemaining = 0L;
        markDurableMutation();
    }

    /**
     * @return immutable offer from the isolated completion buffer
     */
    public Optional<GenericStack> completionOffer() {
        if (!this.completionSealed || this.completionBuffer == 0L) {
            return Optional.empty();
        }
        for (Map.Entry<AEKey, Long> entry : this.actualFinalOutputs.entrySet()) {
            if (entry.getValue() > 0L) {
                return Optional.of(new GenericStack(entry.getKey(), entry.getValue()));
            }
        }
        return Optional.of(new GenericStack(this.targetKey, this.completionBuffer));
    }

    /**
     * Deducts only the amount actually accepted by the requester.
     *
     * @param acceptedAmount positive amount accepted from the completion offer
     */
    public void recordDelivered(AEKey acceptedKey, long acceptedAmount) {
        if (!this.completionSealed || acceptedAmount <= 0L || acceptedAmount > this.completionBuffer ||
                acceptedAmount > this.deliveryRemaining) {
            throw new IllegalArgumentException("A Trinity delivery must deduct a positive accepted completion amount");
        }
        Long actualAmount = this.actualFinalOutputs.get(acceptedKey);
        if (actualAmount != null) {
            if (acceptedAmount > actualAmount) {
                throw new IllegalArgumentException("A Trinity delivery exceeds its actual final-output variant");
            }
            if (acceptedAmount == actualAmount) {
                this.actualFinalOutputs.remove(acceptedKey);
            } else {
                this.actualFinalOutputs.put(acceptedKey, actualAmount - acceptedAmount);
            }
        } else if (!this.targetKey.equals(acceptedKey) || acceptedAmount >
                Math.subtractExact(this.completionBuffer, actualFinalOutputAmount())) {
                    throw new IllegalArgumentException("A Trinity delivery key is absent from its completion buffer");
                }
        this.completionBuffer -= acceptedAmount;
        this.deliveryRemaining -= acceptedAmount;
        markDurableMutation();
    }

    /**
     * Transfers all remaining sealed output to the standalone recovery path exactly once.
     *
     * @return released stack, or empty when the buffer contains nothing
     */
    public Map<AEKey, Long> releaseCompletionForStandalone() {
        LinkedHashMap<AEKey, Long> released = new LinkedHashMap<>(this.actualFinalOutputs);
        long actualAmount = actualFinalOutputAmount();
        if (this.completionSealed) {
            long exactAmount = Math.subtractExact(this.completionBuffer, actualAmount);
            if (exactAmount > 0L) {
                released.merge(this.targetKey, exactAmount, Math::addExact);
            }
            this.deliveryRemaining = Math.subtractExact(this.deliveryRemaining, this.completionBuffer);
            this.completionBuffer = 0L;
        }
        if (!this.actualFinalOutputs.isEmpty() || !released.isEmpty()) {
            this.actualFinalOutputs.clear();
            markDurableMutation();
        }
        return Collections.unmodifiableMap(released);
    }

    /**
     * Returns the exact amount owned in the isolated completion state for one key.
     */
    public long completionAmount(AEKey key) {
        if (!this.completionSealed) {
            return this.actualFinalOutputs.getOrDefault(key, 0L);
        }
        long actual = this.actualFinalOutputs.getOrDefault(key, 0L);
        if (this.targetKey.equals(key)) {
            return Math.addExact(actual, Math.subtractExact(this.completionBuffer, actualFinalOutputAmount()));
        }
        return actual;
    }

    /**
     * @return immutable keyed contents currently isolated from ordinary working inventory
     */
    public Map<AEKey, Long> completionContents() {
        LinkedHashMap<AEKey, Long> contents = new LinkedHashMap<>(this.actualFinalOutputs);
        if (this.completionSealed) {
            long exactAmount = Math.subtractExact(this.completionBuffer, actualFinalOutputAmount());
            if (exactAmount > 0L) {
                contents.merge(this.targetKey, exactAmount, Math::addExact);
            }
        }
        return Collections.unmodifiableMap(contents);
    }

    /**
     * @return exact requested target amount not yet delivered or released
     */
    public long deliveryRemaining() {
        return this.deliveryRemaining;
    }

    /**
     * Returns a transient counter that changes whenever durable execution state changes.
     *
     * <p>
     * The counter is not persisted; callers use it only to decide whether the owning block entity must be marked
     * dirty after a state-machine step.
     * </p>
     *
     * @return current durable-state revision
     */
    public long durableRevision() {
        return this.durableRevision;
    }

    /**
     * Encodes durable state without serializing transient queues or indexes.
     *
     * @param registries  server registry lookup used by AE key codecs
     * @param currentTick current server tick used to persist relative retry delays
     * @return strict execution NBT
     */
    public CompoundTag save(HolderLookup.Provider registries, long currentTick) {
        requireTick(currentTick);
        return TrinityExecutionNbtCodec.encode(snapshot(currentTick), registries);
    }

    private TrinityExecutionSnapshot snapshot(long currentTick) {
        ArrayList<Stage> stageSnapshots = new ArrayList<>();
        for (Integer stageIndex : this.stageOrder) {
            stageSnapshots.add(requireStage(stageIndex).snapshot());
        }
        ArrayList<RepeatBlock> repeatSnapshots = new ArrayList<>();
        this.repeatBlocks.values().forEach(repeat -> repeatSnapshots.add(repeat.snapshot()));
        return new TrinityExecutionSnapshot(
                this.catalogRevision,
                this.quantityMode,
                this.targetKey,
                this.targetAmount,
                status(),
                this.failureReason,
                this.generation,
                stageSnapshots,
                this.stageOrder,
                repeatSnapshots,
                this.seedReserve,
                this.completionSealed,
                this.completionBuffer,
                this.actualFinalOutputs,
                this.deliveryRemaining,
                this.borrowingLedger.entries(),
                currentTick,
                this.budgetRetryAt);
    }

    private void installPlan(TrinityCraftingPlan plan) {
        this.catalogRevision = plan.catalogRevision();
        this.quantityMode = plan.quantityMode();
        this.stages.clear();
        this.stageOrder.clear();
        this.repeatBlocks.clear();
        this.repeatByStage.clear();
        this.seedReserve.clear();

        for (TrinityPlanStage planStage : plan.stages()) {
            StageState stage = StageState.fromPlan(planStage);
            if (this.stages.putIfAbsent(stage.index, stage) != null) {
                throw new IllegalArgumentException("A Trinity execution plan contains duplicate stage indexes");
            }
        }
        this.stageOrder.addAll(plan.stageOrder());
        for (TrinityCycleRepeatBlock planBlock : plan.cycleRepeatBlocks()) {
            RepeatState repeat = RepeatState.fromPlan(planBlock);
            if (this.repeatBlocks.putIfAbsent(repeat.index, repeat) != null) {
                throw new IllegalArgumentException("A Trinity execution plan contains duplicate repeat indexes");
            }
            for (Integer stageIndex : repeat.stageOrder) {
                if (this.repeatByStage.putIfAbsent(stageIndex, repeat) != null) {
                    throw new IllegalArgumentException("A Trinity execution cycle stage belongs to multiple repeats");
                }
            }
        }
        plan.minimumSeed().forEach((key, amount) -> this.seedReserve.put(key, amount.longValueExact()));
        validateInstalledPlan();
    }

    private void adoptInstalledPlan(TrinityPlanExecution prepared) {
        this.catalogRevision = prepared.catalogRevision;
        this.quantityMode = prepared.quantityMode;
        this.stages.clear();
        this.stages.putAll(prepared.stages);
        this.stageOrder.clear();
        this.stageOrder.addAll(prepared.stageOrder);
        this.repeatBlocks.clear();
        this.repeatBlocks.putAll(prepared.repeatBlocks);
        this.repeatByStage.clear();
        this.repeatByStage.putAll(prepared.repeatByStage);
        this.seedReserve.clear();
        this.seedReserve.putAll(prepared.seedReserve);
    }

    private void validateInstalledPlan() {
        if (this.catalogRevision < 0L || this.quantityMode == null ||
                this.stageOrder.size() != this.stages.size() ||
                !new HashSet<>(this.stageOrder).equals(this.stages.keySet())) {
            throw new IllegalArgumentException("A Trinity execution requires complete plan metadata and stage order");
        }
        for (StageState stage : this.stages.values()) {
            if (!this.stages.keySet().containsAll(stage.dependencies) ||
                    stage.cycle != this.repeatByStage.containsKey(stage.index)) {
                throw new IllegalArgumentException("A Trinity execution plan has inconsistent dependencies or cycle membership");
            }
        }
    }

    private void validateRestoredCursors() {
        for (StageState stage : this.stages.values()) {
            if (stage.completed) {
                if (stage.currentFiring != stage.firings.size() ||
                        stage.firings.stream().anyMatch(firing -> firing.remainingCount != 0L) ||
                        stage.waitKind != WaitKind.NONE) {
                    throw new IllegalArgumentException("A completed Trinity stage cannot retain firing or wait work");
                }
                continue;
            }
            if (stage.currentFiring < 0 || stage.currentFiring > stage.firings.size() ||
                    (!stage.cycle && stage.currentFiring == stage.firings.size())) {
                throw new IllegalArgumentException("An unfinished Trinity stage requires a current firing");
            }
            for (int firingIndex = 0; firingIndex < stage.firings.size(); firingIndex++) {
                FiringState firing = stage.firings.get(firingIndex);
                if (firingIndex < stage.currentFiring && firing.remainingCount != 0L) {
                    throw new IllegalArgumentException("A Trinity stage retained work before its current firing");
                }
                if (firingIndex > stage.currentFiring && firing.initialized) {
                    throw new IllegalArgumentException("A Trinity stage initialized a firing after its current cursor");
                }
            }
            validateWait(stage);
            if (!stage.cycle) {
                FiringState current = stage.firings.get(stage.currentFiring);
                if (!current.initialized || current.remainingCount <= 0L ||
                        current.remainingCount > current.plannedCount) {
                    throw new IllegalArgumentException("An unfinished Trinity DAG stage requires positive remaining work");
                }
            }
        }

        HashSet<Integer> cycleStages = new HashSet<>();
        for (RepeatState repeat : this.repeatBlocks.values()) {
            if (!cycleStages.addAll(repeat.stageOrder)) {
                throw new IllegalArgumentException("A Trinity cycle stage appears in multiple restored repeat blocks");
            }
            if (repeat.remainingRepetitions == 0L) {
                if (repeat.waveCount != 0L || repeat.stageOrder.stream().anyMatch(index -> !requireStage(index).completed)) {
                    throw new IllegalArgumentException("A finished Trinity repeat block requires completed stages and no wave");
                }
                continue;
            }
            if (repeat.stageOrder.stream().anyMatch(index -> requireStage(index).completed)) {
                throw new IllegalArgumentException("An unfinished Trinity repeat block cannot contain completed stages");
            }
            if (repeat.waveCount == 0L && repeat.cursor != 0) {
                throw new IllegalArgumentException("A Trinity repeat block without a wave must point at its first stage");
            }
            for (int position = 0; position < repeat.stageOrder.size(); position++) {
                StageState stage = requireStage(repeat.stageOrder.get(position));
                if (position < repeat.cursor &&
                        (stage.currentFiring != stage.firings.size() ||
                                stage.firings.stream().anyMatch(firing -> !firing.initialized))) {
                    throw new IllegalArgumentException("A Trinity repeat stage before the cursor must finish every wave firing");
                }
                if (position > repeat.cursor && stage.firings.stream().anyMatch(firing -> firing.initialized)) {
                    throw new IllegalArgumentException("A Trinity repeat stage after the cursor cannot start its wave");
                }
            }
            StageState active = requireStage(repeat.stageOrder.get(repeat.cursor));
            if (active.currentFiring >= active.firings.size()) {
                throw new IllegalArgumentException("A Trinity active repeat stage requires a current firing");
            }
            FiringState current = active.firings.get(active.currentFiring);
            if (repeat.waveCount == 0L) {
                if (active.currentFiring != 0 || current.initialized) {
                    throw new IllegalArgumentException("A Trinity new wave must begin with an uninitialized first firing");
                }
            } else {
                long maximumRemaining = Math.multiplyExact(current.plannedCount, repeat.waveCount);
                if (current.initialized &&
                        (current.remainingCount <= 0L || current.remainingCount > maximumRemaining)) {
                    throw new IllegalArgumentException("A Trinity active wave requires bounded initialized firing work");
                }
            }
        }
    }

    private void validateCompletionState() {
        if (this.completionBuffer < 0L || this.deliveryRemaining < 0L ||
                this.deliveryRemaining > this.targetAmount || this.completionBuffer > this.deliveryRemaining) {
            throw new IllegalArgumentException("A Trinity completion buffer contains impossible delivery amounts");
        }
        AEItemKey targetItem = this.targetKey instanceof AEItemKey itemKey ? itemKey : null;
        if (targetItem == null && !this.actualFinalOutputs.isEmpty()) {
            throw new IllegalArgumentException("Only item targets can retain actual final-output variants");
        }
        long actualAmount = 0L;
        if (targetItem != null) {
            for (Map.Entry<AEKey, Long> entry : this.actualFinalOutputs.entrySet()) {
                if (!(entry.getKey() instanceof AEItemKey itemKey) || entry.getValue() <= 0L ||
                        itemKey.getItem() != targetItem.getItem()) {
                    throw new IllegalArgumentException(
                            "A Trinity actual final-output buffer contains an invalid item variant");
                }
                actualAmount = Math.addExact(actualAmount, entry.getValue());
            }
        }
        if (actualAmount > this.deliveryRemaining || this.completionSealed && actualAmount > this.completionBuffer) {
            throw new IllegalArgumentException("A Trinity actual final-output buffer exceeds remaining delivery ownership");
        }
        if (!this.completionSealed &&
                (this.completionBuffer != 0L || this.deliveryRemaining != this.targetAmount)) {
            throw new IllegalArgumentException("An unsealed Trinity completion buffer cannot record delivery progress");
        }
        if (this.completionSealed && !productionComplete()) {
            throw new IllegalArgumentException("A Trinity completion buffer cannot be sealed before production completes");
        }
        if (this.completionSealed && this.completionBuffer != this.deliveryRemaining) {
            throw new IllegalArgumentException("A sealed Trinity completion buffer must own every undelivered target unit");
        }
    }

    private void validatePersistedStatusShape(Status persistedStatus) {
        boolean hasInputWait = false;
        boolean hasDynamicWait = false;
        boolean hasProviderWait = false;
        for (StageState stage : this.stages.values()) {
            hasInputWait |= stage.waitKind == WaitKind.INPUT;
            hasDynamicWait |= stage.waitKind == WaitKind.DYNAMIC_INPUT;
            hasProviderWait |= stage.waitKind == WaitKind.PROVIDER;
        }
        switch (persistedStatus) {
            case READY -> {
                if (this.failed || this.planning || productionComplete() || this.budgetRetryAt >= 0L ||
                        this.stages.values().stream().noneMatch(this::eligible)) {
                    throw new IllegalArgumentException("A ready Trinity execution cannot be terminal, planning or budget-gated");
                }
            }
            case WAITING_INPUT -> {
                if (!hasInputWait || this.failed || this.planning || productionComplete() ||
                        this.stages.values().stream().anyMatch(this::eligible)) {
                    throw new IllegalArgumentException("A Trinity input-wait status requires an unfinished input wait");
                }
            }
            case WAITING_DYNAMIC_INPUT -> {
                if (!hasDynamicWait || this.failed || this.planning || productionComplete() ||
                        this.stages.values().stream().anyMatch(this::eligible)) {
                    throw new IllegalArgumentException("A Trinity dynamic-wait status requires an unfinished dynamic wait");
                }
            }
            case WAITING_PROVIDER -> {
                if (!hasProviderWait || this.failed || this.planning || productionComplete() ||
                        this.stages.values().stream().anyMatch(this::eligible)) {
                    throw new IllegalArgumentException("A Trinity provider-wait status requires an unfinished provider wait");
                }
            }
            case PLANNING -> {
                if (!this.planning || this.failed || productionComplete()) {
                    throw new IllegalArgumentException("A Trinity planning status requires unfinished non-failed work");
                }
            }
            case BUDGET_EXHAUSTED -> {
                if (this.failed || this.planning || productionComplete() ||
                        this.stages.values().stream().noneMatch(this::eligible)) {
                    throw new IllegalArgumentException("A Trinity budget status requires unfinished active work");
                }
            }
            case COMPLETED -> {
                if (!productionComplete() || this.failed || this.planning) {
                    throw new IllegalArgumentException("A Trinity completed status requires only completed stages");
                }
            }
            case FAILED -> {
                if (!this.failed || productionComplete()) {
                    throw new IllegalArgumentException("A Trinity failed status requires unfinished failed work");
                }
            }
        }
    }

    private static void validateWait(StageState stage) {
        switch (stage.waitKind) {
            case NONE -> {
                if (!stage.waitingKeys.isEmpty() || stage.retryAt != -1L) {
                    throw new IllegalArgumentException("An active Trinity stage cannot retain wait metadata");
                }
            }
            case INPUT -> {
                if (stage.waitingKeys.isEmpty() || stage.retryAt != -1L) {
                    throw new IllegalArgumentException("A Trinity input wait requires keys and no timed retry");
                }
            }
            case DYNAMIC_INPUT -> {
                if (stage.waitingKeys.isEmpty() || stage.retryAt < 0L) {
                    throw new IllegalArgumentException("A Trinity dynamic wait requires keys and a timed retry");
                }
            }
            case PROVIDER -> {
                if (!stage.waitingKeys.isEmpty() || stage.retryAt < 0L) {
                    throw new IllegalArgumentException("A Trinity provider wait requires a timed retry without keys");
                }
            }
        }
    }

    private void rebuildTransientState(long currentTick) {
        this.currentWork = null;
        this.readyQueue.clear();
        this.queuedStages.clear();
        this.inputStageIndex.clear();
        this.dependents.clear();
        this.retryQueue.clear();
        for (StageState stage : this.stages.values()) {
            stage.leased = false;
            for (Integer dependency : stage.dependencies) {
                this.dependents.computeIfAbsent(dependency, ignored -> new LinkedHashSet<>()).add(stage.index);
            }
            LinkedHashSet<AEKey> indexedKeys = new LinkedHashSet<>(stage.inputKeys);
            indexedKeys.addAll(stage.waitingKeys);
            for (AEKey key : indexedKeys) {
                this.inputStageIndex.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(stage.index);
            }
            if (stage.waitKind.retrying()) {
                scheduleRetry(stage);
            }
        }
        if (!this.failed && !this.planning && !productionComplete()) {
            activateDueRetries(currentTick);
        }
        if (this.budgetRetryAt >= 0L && currentTick >= this.budgetRetryAt) {
            this.budgetRetryAt = -1L;
            markDurableMutation();
        }
        if (!this.failed && !this.planning && !productionComplete()) {
            for (Integer stageIndex : this.stageOrder) {
                enqueueIfEligible(requireStage(stageIndex));
            }
        }
    }

    private Work createWork(StageState stage) {
        initializeCurrentFiring(stage);
        FiringState firing = stage.firings.get(stage.currentFiring);
        long maximum = firing.remainingCount;
        if (stage.cycle) {
            RepeatState repeat = requireRepeat(stage.index);
            if (repeat.waveCount == 0L) {
                if (repeat.cursor != 0 || stage.currentFiring != 0 || firing.initialized) {
                    throw new IllegalStateException("A Trinity cycle has an invalid uninitialized wave cursor");
                }
                maximum = Math.multiplyExact(firing.plannedCount, repeat.remainingRepetitions);
            }
        }
        return new Work(
                this.generation,
                stage.index,
                stage.currentFiring,
                firing.patternIdentity,
                firing.primaryOutput,
                firing.variantOrdinal,
                maximum,
                stage.cycle);
    }

    private void initializeCurrentFiring(StageState stage) {
        if (stage.currentFiring < 0 || stage.currentFiring >= stage.firings.size()) {
            throw new IllegalStateException("A Trinity stage firing cursor is outside its signature");
        }
        FiringState firing = stage.firings.get(stage.currentFiring);
        if (firing.initialized) {
            return;
        }
        if (!stage.cycle) {
            firing.remainingCount = firing.plannedCount;
            firing.initialized = true;
            return;
        }
        RepeatState repeat = requireRepeat(stage.index);
        if (repeat.waveCount > 0L) {
            firing.remainingCount = Math.multiplyExact(firing.plannedCount, repeat.waveCount);
            firing.initialized = true;
        }
    }

    private void finishStage(StageState stage) {
        if (!stage.cycle) {
            stage.completed = true;
            enqueueDependents(stage.index);
            return;
        }
        RepeatState repeat = requireRepeat(stage.index);
        int expectedStage = repeat.stageOrder.get(repeat.cursor);
        if (expectedStage != stage.index || repeat.waveCount <= 0L) {
            throw new IllegalStateException("A Trinity cycle stage completed outside its active wave cursor");
        }
        if (repeat.cursor + 1 < repeat.stageOrder.size()) {
            repeat.cursor++;
            StageState next = requireStage(repeat.stageOrder.get(repeat.cursor));
            next.resetForCycleWave();
            enqueueIfEligible(next);
            return;
        }

        repeat.remainingRepetitions = Math.subtractExact(repeat.remainingRepetitions, repeat.waveCount);
        if (repeat.remainingRepetitions < 0L) {
            throw new IllegalStateException("A Trinity cycle wave exceeded its remaining repetitions");
        }
        if (repeat.remainingRepetitions == 0L) {
            for (Integer stageIndex : repeat.stageOrder) {
                StageState completed = requireStage(stageIndex);
                completed.completed = true;
                enqueueDependents(stageIndex);
            }
            repeat.waveCount = 0L;
            return;
        }

        repeat.cursor = 0;
        repeat.waveCount = 0L;
        for (Integer stageIndex : repeat.stageOrder) {
            requireStage(stageIndex).resetForCycleWave();
        }
        enqueueIfEligible(requireStage(repeat.stageOrder.getFirst()));
    }

    private void enqueueDependents(int stageIndex) {
        Set<Integer> affected = this.dependents.get(stageIndex);
        if (affected == null) {
            return;
        }
        for (Integer dependent : affected) {
            enqueueIfEligible(requireStage(dependent));
        }
    }

    private void enqueueIfEligible(StageState stage) {
        if (eligible(stage) && this.queuedStages.add(stage.index)) {
            this.readyQueue.addLast(stage.index);
        }
    }

    private boolean eligible(StageState stage) {
        if (stage.completed || stage.leased || stage.waitKind != WaitKind.NONE) {
            return false;
        }
        if (!stage.cycle) {
            return dependenciesComplete(stage, null);
        }
        RepeatState repeat = requireRepeat(stage.index);
        return repeat.remainingRepetitions > 0L && repeat.stageOrder.get(repeat.cursor) == stage.index &&
                dependenciesComplete(stage, repeat);
    }

    private boolean dependenciesComplete(StageState stage, RepeatState repeat) {
        for (Integer dependency : stage.dependencies) {
            if (repeat != null) {
                int dependencyPosition = repeat.stageOrder.indexOf(dependency);
                if (dependencyPosition >= 0 && dependencyPosition < repeat.cursor) {
                    continue;
                }
            }
            if (!requireStage(dependency).completed) {
                return false;
            }
        }
        return true;
    }

    private StageState releaseCurrentWork(Work work) {
        StageState stage = requireCurrentWork(work);
        clearCurrentWork(stage);
        return stage;
    }

    private StageState requireCurrentWork(Work work) {
        if (this.currentWork == null || !this.currentWork.equals(work)) {
            throw new IllegalStateException("A Trinity execution event referenced stale or unleased work");
        }
        StageState stage = requireStage(work.stageIndex());
        if (!stage.leased || stage.currentFiring != work.firingIndex()) {
            throw new IllegalStateException("A Trinity execution work lease no longer matches its stage cursor");
        }
        return stage;
    }

    private void clearCurrentWork(StageState stage) {
        stage.leased = false;
        this.currentWork = null;
    }

    private void beginWait(StageState stage, WaitKind kind, Set<AEKey> keys, long retryAt) {
        if (stage.waitKind != WaitKind.NONE || !stage.waitingKeys.isEmpty() || stage.retryAt != -1L) {
            throw new IllegalStateException("A Trinity stage cannot begin a second wait before the first is released");
        }
        stage.waitKind = kind;
        stage.waitingKeys.addAll(keys);
        stage.retryAt = retryAt;
        stage.retryVersion = Math.addExact(stage.retryVersion, 1L);
    }

    private void clearWait(StageState stage) {
        boolean hadWait = stage.waitKind != WaitKind.NONE || !stage.waitingKeys.isEmpty() || stage.retryAt != -1L;
        stage.waitKind = WaitKind.NONE;
        stage.waitingKeys.clear();
        stage.retryAt = -1L;
        if (hadWait) {
            stage.retryVersion = Math.addExact(stage.retryVersion, 1L);
        }
    }

    private void scheduleRetry(StageState stage) {
        if (!stage.waitKind.retrying() || stage.retryAt < 0L) {
            throw new IllegalStateException("A Trinity retry requires a retrying wait state and tick");
        }
        this.retryQueue.add(new RetryEntry(stage.retryAt, stage.index, stage.retryVersion, stage.waitKind));
    }

    private void activateDueRetries(long currentTick) {
        boolean changed = false;
        while (!this.retryQueue.isEmpty() && this.retryQueue.peek().retryAt <= currentTick) {
            RetryEntry retry = this.retryQueue.remove();
            StageState stage = requireStage(retry.stageIndex);
            if (stage.retryVersion != retry.version || stage.waitKind != retry.waitKind ||
                    stage.retryAt != retry.retryAt) {
                continue;
            }
            clearWait(stage);
            enqueueIfEligible(stage);
            changed = true;
        }
        if (changed) {
            markDurableMutation();
        }
    }

    private void markDurableMutation() {
        this.durableRevision = Math.incrementExact(this.durableRevision);
    }

    private StageState requireStage(int stageIndex) {
        StageState stage = this.stages.get(stageIndex);
        if (stage == null) {
            throw new IllegalArgumentException("Unknown Trinity execution stage " + stageIndex);
        }
        return stage;
    }

    private RepeatState requireRepeat(int stageIndex) {
        RepeatState repeat = this.repeatByStage.get(stageIndex);
        if (repeat == null) {
            throw new IllegalStateException("A Trinity cycle stage is missing its repeat block");
        }
        return repeat;
    }

    private static Set<AEKey> copyKeys(Set<AEKey> keys, String role) {
        if (keys == null) {
            throw new IllegalArgumentException("A Trinity " + role + " key set is required");
        }
        LinkedHashSet<AEKey> copied = new LinkedHashSet<>();
        for (AEKey key : keys) {
            if (key == null) {
                throw new IllegalArgumentException("A Trinity " + role + " cannot contain a null key");
            }
            copied.add(key);
        }
        return Collections.unmodifiableSet(copied);
    }

    private static Set<AEKey> copyNonEmptyKeys(Set<AEKey> keys, String role) {
        Set<AEKey> copied = copyKeys(keys, role);
        if (copied.isEmpty()) {
            throw new IllegalArgumentException("A Trinity " + role + " requires at least one key");
        }
        return copied;
    }

    private static void requireTick(long currentTick) {
        if (currentTick < 0L) {
            throw new IllegalArgumentException("A Trinity execution tick cannot be negative");
        }
    }

    private static long rebaseRetryAt(long retryAt, long savedAtTick, long currentTick) {
        if (retryAt < 0L) {
            return -1L;
        }
        if (savedAtTick < 0L || retryAt <= savedAtTick) {
            return currentTick;
        }
        return Math.addExact(currentTick, Math.subtractExact(retryAt, savedAtTick));
    }

    private static void requireRetryCap(int maxRetryTicks) {
        if (maxRetryTicks <= 0) {
            throw new IllegalArgumentException("A Trinity retry cap must be positive");
        }
    }

    private static int nextDelay(int current, int maximum) {
        if (current >= maximum) {
            return maximum;
        }
        return (int) Math.min(maximum, Math.multiplyExact(current, 2L));
    }

    private static long ceilDiv(long amount, long divisor) {
        return 1L + (amount - 1L) / divisor;
    }

    /**
     * Rebuilds the exact one-wave seed lower bound from durable stage balances.
     *
     * <p>
     * Repeat-block NBT predates a dedicated seed field. Keeping this reconstruction here preserves every existing
     * schema while retaining the resource contract required to start a newly restored cycle wave.
     * </p>
     */
    private static Map<AEKey, Long> reconstructMinimumSeed(List<Integer> stageOrder,
                                                           Map<Integer, StageState> stages) {
        LinkedHashMap<AEKey, Long> seed = new LinkedHashMap<>();
        LinkedHashMap<AEKey, Long> balances = new LinkedHashMap<>();
        for (Integer stageIndex : stageOrder) {
            StageState stage = stages.get(stageIndex);
            if (stage == null || !stage.cycle) {
                throw new IllegalArgumentException("A Trinity repeat block references an invalid cycle stage");
            }
            for (Map.Entry<AEKey, Long> required : stage.requiredAtStart.entrySet()) {
                long balance = balances.getOrDefault(required.getKey(), 0L);
                if (balance >= required.getValue()) {
                    continue;
                }
                long deficit = Math.subtractExact(required.getValue(), balance);
                seed.merge(required.getKey(), deficit, Math::addExact);
                balances.put(required.getKey(), Math.addExact(balance, deficit));
            }
            for (Map.Entry<AEKey, Long> change : stage.netChange.entrySet()) {
                long updated = Math.addExact(balances.getOrDefault(change.getKey(), 0L), change.getValue());
                if (updated < 0L) {
                    throw new IllegalArgumentException("A Trinity repeat block has an impossible negative stage balance");
                }
                if (updated == 0L) {
                    balances.remove(change.getKey());
                } else {
                    balances.put(change.getKey(), updated);
                }
            }
        }
        return Collections.unmodifiableMap(seed);
    }

    private record RetryEntry(long retryAt, int stageIndex, long version, WaitKind waitKind) {}

    private static final class FiringState {

        private final TrinityPatternIdentity patternIdentity;
        private final AEKey primaryOutput;
        private final int variantOrdinal;
        private final long plannedCount;
        private final LinkedHashMap<AEKey, Long> outputs;
        private long remainingCount;
        private boolean initialized;

        private FiringState(TrinityPatternIdentity patternIdentity,
                            AEKey primaryOutput,
                            int variantOrdinal,
                            long plannedCount,
                            Map<AEKey, Long> outputs,
                            long remainingCount,
                            boolean initialized) {
            if (variantOrdinal < 0 || plannedCount <= 0L || remainingCount < 0L ||
                    (!initialized && remainingCount != 0L)) {
                throw new IllegalArgumentException("A Trinity firing state contains an invalid signature or cursor");
            }
            this.patternIdentity = patternIdentity;
            this.primaryOutput = primaryOutput;
            this.variantOrdinal = variantOrdinal;
            this.plannedCount = plannedCount;
            this.outputs = copyOutputs(outputs);
            this.remainingCount = remainingCount;
            this.initialized = initialized;
        }

        private static FiringState fromPlan(TrinityPlanPatternFiring firing, boolean cycle) {
            long count = firing.count().longValueExact();
            return new FiringState(
                    firing.patternIdentity(),
                    firing.primaryOutput(),
                    firing.variantOrdinal(),
                    count,
                    exactOutputs(firing.outputs()),
                    cycle ? 0L : count,
                    !cycle);
        }

        private static FiringState fromSnapshot(Firing snapshot) {
            return new FiringState(
                    snapshot.patternIdentity(),
                    snapshot.primaryOutput(),
                    snapshot.variantOrdinal(),
                    snapshot.plannedCount(),
                    snapshot.outputs(),
                    snapshot.remainingCount(),
                    snapshot.initialized());
        }

        private Firing snapshot() {
            return new Firing(
                    this.patternIdentity,
                    this.primaryOutput,
                    this.variantOrdinal,
                    this.plannedCount,
                    this.outputs,
                    this.remainingCount,
                    this.initialized);
        }

        private static LinkedHashMap<AEKey, Long> exactOutputs(Map<AEKey, BigInteger> source) {
            LinkedHashMap<AEKey, Long> outputs = new LinkedHashMap<>();
            source.forEach((key, amount) -> outputs.put(key, amount.longValueExact()));
            return outputs;
        }

        private static LinkedHashMap<AEKey, Long> copyOutputs(Map<AEKey, Long> source) {
            LinkedHashMap<AEKey, Long> outputs = new LinkedHashMap<>();
            source.forEach((key, amount) -> {
                if (key == null || amount == null || amount <= 0L) {
                    throw new IllegalArgumentException("A Trinity firing output must be positive");
                }
                outputs.put(key, amount);
            });
            return outputs;
        }
    }

    private static final class StageState {

        private final int index;
        private final boolean cycle;
        private final LinkedHashSet<Integer> dependencies;
        private final ArrayList<FiringState> firings;
        private final LinkedHashMap<AEKey, Long> requiredAtStart;
        private final LinkedHashMap<AEKey, Long> netChange;
        private final LinkedHashSet<AEKey> inputKeys = new LinkedHashSet<>();
        private final LinkedHashSet<AEKey> waitingKeys = new LinkedHashSet<>();
        private int currentFiring;
        private boolean completed;
        private WaitKind waitKind = WaitKind.NONE;
        private long retryAt = -1L;
        private int nextDynamicDelay = 1;
        private int nextProviderDelay = 1;
        private long retryVersion;
        private boolean leased;

        private StageState(int index,
                           boolean cycle,
                           Set<Integer> dependencies,
                           List<FiringState> firings,
                           Map<AEKey, Long> requiredAtStart,
                           Map<AEKey, Long> netChange) {
            if (index < 0 || firings.isEmpty()) {
                throw new IllegalArgumentException("A Trinity stage state requires index, dependencies and firings");
            }
            this.index = index;
            this.cycle = cycle;
            this.dependencies = new LinkedHashSet<>(dependencies);
            this.firings = new ArrayList<>(firings);
            this.requiredAtStart = copyAmounts(requiredAtStart, false, "stage start requirement");
            this.netChange = copyAmounts(netChange, true, "stage net change");
        }

        private static StageState fromPlan(TrinityPlanStage stage) {
            ArrayList<FiringState> firings = new ArrayList<>();
            stage.firings().forEach(firing -> firings.add(FiringState.fromPlan(firing, stage.cycleStage())));
            return new StageState(
                    stage.index(),
                    stage.cycleStage(),
                    stage.dependencies(),
                    firings,
                    exactAmounts(stage.requiredAtStart()),
                    exactAmounts(stage.netChange()));
        }

        private static StageState fromSnapshot(Stage snapshot, long savedAtTick, long currentTick) {
            ArrayList<FiringState> firings = new ArrayList<>();
            snapshot.firings().forEach(firing -> firings.add(FiringState.fromSnapshot(firing)));
            StageState restored = new StageState(
                    snapshot.index(),
                    snapshot.cycle(),
                    new LinkedHashSet<>(snapshot.dependencies()),
                    firings,
                    snapshot.requiredAtStart(),
                    snapshot.netChange());
            restored.currentFiring = snapshot.currentFiring();
            restored.completed = snapshot.completed();
            restored.inputKeys.addAll(snapshot.inputKeys());
            restored.waitingKeys.addAll(snapshot.waitingKeys());
            restored.waitKind = snapshot.waitKind();
            restored.retryAt = rebaseRetryAt(snapshot.retryAt(), savedAtTick, currentTick);
            restored.nextDynamicDelay = snapshot.nextDynamicDelay();
            restored.nextProviderDelay = snapshot.nextProviderDelay();
            restored.retryVersion = snapshot.retryVersion();
            return restored;
        }

        private void resetForCycleWave() {
            if (!this.cycle || this.completed || this.leased) {
                throw new IllegalStateException("Only an unfinished idle Trinity cycle stage may reset for a wave");
            }
            this.currentFiring = 0;
            this.waitKind = WaitKind.NONE;
            this.waitingKeys.clear();
            this.retryAt = -1L;
            for (FiringState firing : this.firings) {
                firing.remainingCount = 0L;
                firing.initialized = false;
            }
        }

        private Stage snapshot() {
            ArrayList<Firing> firingSnapshots = new ArrayList<>();
            this.firings.forEach(firing -> firingSnapshots.add(firing.snapshot()));
            return new Stage(
                    this.index,
                    this.cycle,
                    List.copyOf(this.dependencies),
                    this.currentFiring,
                    this.completed,
                    this.inputKeys,
                    this.waitingKeys,
                    this.waitKind,
                    this.retryAt,
                    this.nextDynamicDelay,
                    this.nextProviderDelay,
                    this.retryVersion,
                    firingSnapshots,
                    this.requiredAtStart,
                    this.netChange);
        }

        private static LinkedHashMap<AEKey, Long> exactAmounts(Map<AEKey, BigInteger> source) {
            LinkedHashMap<AEKey, Long> exact = new LinkedHashMap<>();
            source.forEach((key, amount) -> exact.put(key, amount.longValueExact()));
            return exact;
        }

        private static LinkedHashMap<AEKey, Long> copyAmounts(Map<AEKey, Long> source,
                                                              boolean signed,
                                                              String role) {
            LinkedHashMap<AEKey, Long> copied = new LinkedHashMap<>();
            source.forEach((key, amount) -> {
                if (signed ? amount == 0L : amount <= 0L) {
                    throw new IllegalArgumentException("A Trinity " + role + " contains an invalid amount");
                }
                copied.put(key, amount);
            });
            return copied;
        }
    }

    private static final class RepeatState {

        private final int index;
        private final ArrayList<Integer> stageOrder;
        private final Map<AEKey, Long> minimumSeed;
        private long remainingRepetitions;
        private int cursor;
        private long waveCount;

        private RepeatState(int index,
                            List<Integer> stageOrder,
                            Map<AEKey, Long> minimumSeed,
                            long remainingRepetitions,
                            int cursor,
                            long waveCount) {
            if (index < 0 || stageOrder.isEmpty() || remainingRepetitions < 0L ||
                    minimumSeed == null || cursor < 0 || cursor >= stageOrder.size() || waveCount < 0L ||
                    waveCount > remainingRepetitions) {
                throw new IllegalArgumentException("A Trinity repeat state contains an invalid cursor or count");
            }
            HashSet<Integer> uniqueStages = new HashSet<>();
            for (Integer stageIndex : stageOrder) {
                if (stageIndex < 0 || !uniqueStages.add(stageIndex)) {
                    throw new IllegalArgumentException("A Trinity repeat state requires unique non-negative stages");
                }
            }
            this.index = index;
            this.stageOrder = new ArrayList<>(stageOrder);
            this.minimumSeed = copyMinimumSeed(minimumSeed);
            this.remainingRepetitions = remainingRepetitions;
            this.cursor = cursor;
            this.waveCount = waveCount;
        }

        private static RepeatState fromPlan(TrinityCycleRepeatBlock block) {
            LinkedHashMap<AEKey, Long> minimumSeed = new LinkedHashMap<>();
            block.minimumSeed().forEach((key, amount) -> minimumSeed.put(key, amount.longValueExact()));
            return new RepeatState(
                    block.index(),
                    block.stageOrder(),
                    minimumSeed,
                    block.repetitions().longValueExact(),
                    0,
                    0L);
        }

        private static RepeatState fromSnapshot(RepeatBlock snapshot, Map<Integer, StageState> stages) {
            return new RepeatState(
                    snapshot.index(),
                    snapshot.stageOrder(),
                    reconstructMinimumSeed(snapshot.stageOrder(), stages),
                    snapshot.remainingRepetitions(),
                    snapshot.cursor(),
                    snapshot.waveCount());
        }

        private RepeatBlock snapshot() {
            return new RepeatBlock(
                    this.index,
                    this.stageOrder,
                    this.remainingRepetitions,
                    this.cursor,
                    this.waveCount);
        }

        private static Map<AEKey, Long> copyMinimumSeed(Map<AEKey, Long> source) {
            LinkedHashMap<AEKey, Long> copied = new LinkedHashMap<>();
            source.forEach((key, amount) -> {
                if (key == null || amount == null || amount <= 0L) {
                    throw new IllegalArgumentException("A Trinity repeat minimum seed must be positive");
                }
                copied.put(key, amount);
            });
            return Collections.unmodifiableMap(copied);
        }
    }
}
