package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state;

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
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.projection.TrinityAe2AmountProjection;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.sameitem.TrinitySameItemPolicy;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectHeapPriorityQueue;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import org.jspecify.annotations.Nullable;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Event-driven, persistence-safe execution cursor for a compact Trinity crafting plan.
 * <p>
 * Deterministic execution implementation with transient event indexes and versioned persistence.
 */
public final class TrinityPlanExecution {

    private static final BigInteger MAX_PHYSICAL_AMOUNT = BigInteger.valueOf(Long.MAX_VALUE);

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
                    plannedVariantOrdinal < 0 ||
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
            if (maximumLogicalFirings < 0L) {
                throw new IllegalArgumentException("A Trinity cycle wave limit requires a non-negative count and key set");
            }
            observedKeys = Set.copyOf(observedKeys);
        }
    }

    private final AEKey targetKey;
    private final BigInteger targetAmount;
    private final Int2ObjectLinkedOpenHashMap<StageState> stages = new Int2ObjectLinkedOpenHashMap<>();
    private final IntArrayList stageOrder = new IntArrayList();
    private final Int2ObjectLinkedOpenHashMap<RepeatState> repeatBlocks = new Int2ObjectLinkedOpenHashMap<>();
    private final Int2ObjectOpenHashMap<RepeatState> repeatByStage = new Int2ObjectOpenHashMap<>();
    private final Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> seedReserve = new Object2ObjectLinkedOpenHashMap<>();
    private final TrinityBorrowingLedger borrowingLedger;

    private final IntLinkedOpenHashSet readyQueue = new IntLinkedOpenHashSet();
    private final Object2ObjectOpenHashMap<AEKey, IntLinkedOpenHashSet> inputStageIndex = new Object2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<IntLinkedOpenHashSet> dependents = new Int2ObjectOpenHashMap<>();
    private final ObjectHeapPriorityQueue<RetryEntry> retryQueue = new ObjectHeapPriorityQueue<>(Comparator
            .comparingLong(RetryEntry::retryAt)
            .thenComparingInt(RetryEntry::stageIndex)
            .thenComparingLong(RetryEntry::version));

    private long catalogRevision;
    private CraftingQuantityMode quantityMode;
    private TrinitySameItemPolicy sameItemPolicy = TrinitySameItemPolicy.empty();
    private long generation;
    private long durableRevision;
    /**
     * Server-thread leases keyed by stage so independent stages can keep proposals in flight together.
     */
    private final Int2ObjectLinkedOpenHashMap<Work> leasedWorks = new Int2ObjectLinkedOpenHashMap<>();
    private boolean planning;
    private boolean failed;
    private String failureReason = "";
    private long budgetRetryAt = -1L;
    private boolean completionSealed;
    private BigInteger completionBuffer = BigInteger.ZERO;
    private BigInteger deliveryRemaining;
    private final Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> actualFinalOutputs = new Object2ObjectLinkedOpenHashMap<>();

    private TrinityPlanExecution(AEKey targetKey,
                                 BigInteger targetAmount,
                                 TrinityBorrowingLedger borrowingLedger) {
        if (targetAmount.signum() <= 0) {
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
        if (currentTick < 0L) {
            throw new IllegalArgumentException("A Trinity execution requires a plan and non-negative server tick");
        }
        GenericStack output = plan.finalOutput();
        TrinityPlanExecution execution = new TrinityPlanExecution(
                output.what(),
                BigInteger.valueOf(output.amount()),
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
        restored.sameItemPolicy = snapshot.sameItemPolicy();
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
            for (int stageIndex : repeat.stageOrder) {
                if (restored.repeatByStage.putIfAbsent(stageIndex, repeat) != null) {
                    throw new IllegalArgumentException("A Trinity cycle stage belongs to multiple restored repeats");
                }
            }
        }
        restored.seedReserve.putAll(snapshot.seedReserve());
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

        if (restored.recomputeRestoredDependencies()) {
            restored.markDurableMutation();
            restored.validateInstalledPlan();
        }
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
        if (!this.leasedWorks.isEmpty() || !this.readyQueue.isEmpty()) {
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
        return new GenericStack(this.targetKey, TrinityAe2AmountProjection.toAe2Amount(this.targetAmount));
    }

    /**
     * Selects one uninspected work for a multi-proposal worker pass. Existing leases that can make immediate progress
     * are preferred, then a new eligible stage is leased when admission permits it. Pending or backoff-blocked leases
     * remain retained for their own completion/retry event. Each stage may be selected at most once per pass.
     *
     * @param currentTick      current server tick used to activate due retries
     * @param inspectedStages  stage indexes already visited by the current bounded worker pass
     * @param workDispatchable exact-work predicate identifying leases able to make immediate progress
     * @param allowNewLease    whether this pass may lease and submit another stage rather than only settle slots
     * @return next actionable, new, or pending work in deterministic order
     */
    public Optional<Work> pollDispatchable(
                                           long currentTick,
                                           Set<Integer> inspectedStages,
                                           Predicate<Work> workDispatchable,
                                           boolean allowNewLease) {
        requireTick(currentTick);
        if (this.failed || this.planning || productionComplete()) {
            return Optional.empty();
        }
        activateDueRetries(currentTick);
        if (this.budgetRetryAt >= 0L && currentTick >= this.budgetRetryAt) {
            this.budgetRetryAt = -1L;
            markDurableMutation();
        }
        if (this.budgetRetryAt >= 0L) {
            return Optional.empty();
        }

        for (Work leased : this.leasedWorks.values()) {
            if (inspectedStages.contains(leased.stageIndex())) {
                continue;
            }
            if (workDispatchable.test(leased)) {
                return Optional.of(leased);
            }
        }

        return allowNewLease ? dequeueEligibleWork(inspectedStages) : Optional.empty();
    }

    /**
     * Returns whether the current execution can settle an existing lease or create a permitted new lease without
     * waiting for another proposal-completion event.
     *
     * @param workDispatchable exact-work predicate identifying leases able to make immediate progress
     * @param allowNewLease    whether current admission/backoff policy permits leasing a new ready stage
     * @return whether a worker dispatch step can make immediate proposal progress
     */
    public boolean hasDispatchableWork(Predicate<Work> workDispatchable, boolean allowNewLease) {
        if (this.failed || this.planning || productionComplete() || this.budgetRetryAt >= 0L) {
            return false;
        }
        for (Work leased : this.leasedWorks.values()) {
            if (workDispatchable.test(leased)) {
                return true;
            }
        }
        if (!allowNewLease) {
            return false;
        }
        for (int stageIndex : this.readyQueue) {
            StageState stage = requireStage(stageIndex);
            if (!this.leasedWorks.containsKey(stage.index) && eligible(stage)) {
                return true;
            }
        }
        return false;
    }

    private Optional<Work> dequeueEligibleWork(Set<Integer> excludedStages) {
        int candidates = this.readyQueue.size();
        while (candidates-- > 0 && !this.readyQueue.isEmpty()) {
            int stageIndex = this.readyQueue.removeFirstInt();
            if (excludedStages.contains(stageIndex)) {
                this.readyQueue.add(stageIndex);
                continue;
            }
            StageState stage = requireStage(stageIndex);
            if (!eligible(stage)) {
                continue;
            }
            if (this.leasedWorks.containsKey(stage.index)) {
                continue;
            }
            Work work = createWork(stage);
            this.leasedWorks.put(stage.index, work);
            stage.leased = true;
            return Optional.of(work);
        }
        return Optional.empty();
    }

    /**
     * @return number of unique stage indexes currently held in the transient ready queue
     */
    public int queuedStageCount() {
        return this.readyQueue.size();
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
        Set<AEKey> copied = normalizeObservedKeys(keys);
        boolean changed = false;
        for (AEKey key : copied) {
            if (stage.inputKeys.add(key)) {
                this.inputStageIndex.computeIfAbsent(key, ignored -> new IntLinkedOpenHashSet()).add(stageIndex);
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
        AEKey observedKey = this.sameItemPolicy.normalizeKey(key);
        IntSet indexed = this.inputStageIndex.get(observedKey);
        if (indexed == null || indexed.isEmpty()) {
            return false;
        }
        boolean released = false;
        for (int stageIndex : indexed.toIntArray()) {
            StageState stage = requireStage(stageIndex);
            if ((stage.waitKind == WaitKind.INPUT || stage.waitKind == WaitKind.DYNAMIC_INPUT) &&
                    stage.waitingKeys.contains(observedKey)) {
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
        Set<AEKey> copied = normalizeNonEmptyObservedKeys(keys, "input wait");
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
        Set<AEKey> copied = normalizeNonEmptyObservedKeys(keys, "dynamic input wait");
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
        if (!this.planning) {
            throw new IllegalStateException("A Trinity replacement plan is accepted only while planning");
        }
        GenericStack output = replacement.finalOutput();
        if (!this.targetKey.equals(output.what()) || replacement.quantityMode() != this.quantityMode ||
                BigInteger.valueOf(output.amount()).compareTo(this.deliveryRemaining) > 0) {
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
     * <p>
     * For an unstarted cycle wave, {@code offeredLogicalFirings} is the seed-safe logical offer calculated before
     * provider capacity slicing. It must not be replaced with {@code acceptedCount}: a provider may accept only a
     * partial physical slice, but the established wave still has to retain its original logical size.
     * </p>
     *
     * @param work                  current leased work
     * @param acceptedCount         positive accepted logical firing count
     * @param offeredLogicalFirings logical offer that established an unstarted cycle wave
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
        if (stage.cycle && repeat.waveCount.signum() == 0) {
            if (repeat.cursor != 0 || stage.currentFiring != 0) {
                throw new IllegalStateException("A Trinity cycle wave must begin at its first stage and firing");
            }
            repeat.waveCount = repeat.remainingRepetitions.min(ceilDiv(
                    BigInteger.valueOf(offeredLogicalFirings),
                    firing.plannedCount));
            if (repeat.waveCount.signum() <= 0) {
                throw new IllegalStateException("A Trinity cycle wave offer cannot establish an empty wave");
            }
            firing.remainingCount = firing.plannedCount.multiply(repeat.waveCount);
            firing.initialized = true;
        }
        BigInteger accepted = BigInteger.valueOf(acceptedCount);
        if (!firing.initialized || firing.remainingCount.compareTo(accepted) < 0) {
            throw new IllegalStateException("A Trinity firing cursor cannot consume beyond its initialized wave");
        }
        firing.remainingCount = firing.remainingCount.subtract(accepted);
        clearCurrentWork(stage);
        stage.nextDynamicDelay = 1;
        stage.nextProviderDelay = 1;
        markDurableMutation();

        if (firing.remainingCount.signum() > 0) {
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
        if (hasInFlight || this.failed || this.planning || productionComplete() || !this.leasedWorks.isEmpty() ||
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
        if (reason.isBlank()) {
            throw new IllegalArgumentException("A Trinity execution failure requires a diagnostic reason");
        }
        if (productionComplete()) {
            throw new IllegalStateException("A completed Trinity execution cannot fail");
        }
        this.failed = true;
        this.failureReason = reason;
        this.planning = false;
        this.budgetRetryAt = -1L;
        this.leasedWorks.clear();
        this.readyQueue.clear();
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
    public Map<AEKey, BigInteger> seedReserve() {
        return Collections.unmodifiableMap(new Object2ObjectLinkedOpenHashMap<>(this.seedReserve));
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
    public CycleWaveLimit maximumCycleLogicalFirings(
                                                     Work work,
                                                     BiFunction<AEKey, BigInteger, BigInteger> availableAmount) {
        StageState stage = requireCurrentWork(work);
        if (!stage.cycle) {
            throw new IllegalArgumentException("Only Trinity cycle work has a seed-limited wave");
        }
        RepeatState repeat = requireRepeat(stage.index);
        if (repeat.waveCount.signum() > 0) {
            return new CycleWaveLimit(work.maximumLogicalFirings(), repeat.minimumSeed.keySet());
        }
        if (repeat.cursor != 0 || repeat.stageOrder.getFirst() != stage.index || stage.currentFiring != 0) {
            throw new IllegalStateException("An unstarted Trinity cycle wave must begin at its first stage and firing");
        }

        FiringState firing = stage.firings.get(stage.currentFiring);
        BigInteger repetitions = repeat.remainingRepetitions;
        for (Map.Entry<AEKey, BigInteger> seed : repeat.minimumSeed.entrySet()) {
            BigInteger usefulUpper = seed.getValue().multiply(repetitions);
            BigInteger available = availableAmount.apply(seed.getKey(), usefulUpper);
            if (available.signum() < 0) {
                throw new IllegalArgumentException("A Trinity cycle seed availability cannot be negative");
            }
            repetitions = repetitions.min(available.divide(seed.getValue()));
        }
        return new CycleWaveLimit(
                physicalWindow(firing.plannedCount.multiply(repetitions)),
                repeat.minimumSeed.keySet());
    }

    /**
     * Reconstructs the exact pattern-declared outputs of every undispatched firing without expanding cycle repeats.
     *
     * @return immutable pending-output projection for AE2's crafting CPU status table
     */
    public Map<AEKey, BigInteger> pendingOutputs() {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> outputs = new Object2ObjectLinkedOpenHashMap<>();
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

    private void addDagPendingOutputs(Map<AEKey, BigInteger> outputs, StageState stage) {
        if (stage.completed) {
            return;
        }
        for (int index = stage.currentFiring; index < stage.firings.size(); index++) {
            FiringState firing = stage.firings.get(index);
            BigInteger remaining = index == stage.currentFiring && firing.initialized ?
                    firing.remainingCount :
                    firing.plannedCount;
            mergePendingOutputs(outputs, firing, remaining);
        }
    }

    private void addCyclePendingOutputs(Map<AEKey, BigInteger> outputs, RepeatState repeat) {
        if (repeat.remainingRepetitions.signum() == 0) {
            return;
        }
        for (int stagePosition = 0; stagePosition < repeat.stageOrder.size(); stagePosition++) {
            StageState stage = requireStage(repeat.stageOrder.getInt(stagePosition));
            for (int firingIndex = 0; firingIndex < stage.firings.size(); firingIndex++) {
                FiringState firing = stage.firings.get(firingIndex);
                BigInteger remaining = cycleFiringRemainder(repeat, stage, stagePosition, firing, firingIndex);
                mergePendingOutputs(outputs, firing, remaining);
            }
        }
    }

    private static BigInteger cycleFiringRemainder(RepeatState repeat,
                                                   StageState stage,
                                                   int stagePosition,
                                                   FiringState firing,
                                                   int firingIndex) {
        if (repeat.waveCount.signum() == 0 || stagePosition > repeat.cursor ||
                (stagePosition == repeat.cursor && firingIndex > stage.currentFiring)) {
            return firing.plannedCount.multiply(repeat.remainingRepetitions);
        }
        BigInteger laterWaves = repeat.remainingRepetitions.subtract(repeat.waveCount);
        BigInteger laterCount = firing.plannedCount.multiply(laterWaves);
        if (stagePosition < repeat.cursor || firingIndex < stage.currentFiring) {
            return laterCount;
        }
        BigInteger activeCount = firing.initialized ?
                firing.remainingCount :
                firing.plannedCount.multiply(repeat.waveCount);
        return activeCount.add(laterCount);
    }

    private void mergePendingOutputs(Map<AEKey, BigInteger> outputs,
                                     FiringState firing,
                                     BigInteger firingCount) {
        if (firingCount.signum() == 0) {
            return;
        }
        firing.outputs.forEach((key, perFiring) -> outputs.merge(
                this.sameItemPolicy.normalizeKey(key),
                perFiring.multiply(firingCount),
                BigInteger::add));
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
    public void sealCompletion(BigInteger amount) {
        BigInteger actualAmount = actualFinalOutputAmount();
        if (!productionComplete() || this.failed || this.completionSealed || amount.signum() < 0 ||
                !amount.add(actualAmount).equals(this.deliveryRemaining)) {
            throw new IllegalStateException("A Trinity completion buffer requires one exact post-production delivery seal");
        }
        this.completionSealed = true;
        this.completionBuffer = amount.add(actualAmount);
        markDurableMutation();
    }

    /**
     * Retains an actual same-item final output outside the working inventory so exact downstream inputs cannot use it.
     *
     * @param actualKey actual machine-returned key with all Data Components intact
     * @param amount    positive accepted amount
     */
    public void recordActualFinalOutput(AEItemKey actualKey, BigInteger amount) {
        if (this.completionSealed || amount.signum() <= 0 || !(this.targetKey instanceof AEItemKey targetItem) ||
                actualKey.getItem() != targetItem.getItem()) {
            throw new IllegalArgumentException("An actual Trinity final output must match the requested registered item");
        }
        BigInteger actualAmount = actualFinalOutputAmount();
        if (amount.compareTo(this.deliveryRemaining.subtract(actualAmount)) > 0) {
            throw new IllegalArgumentException("Actual Trinity final outputs exceed the undelivered request");
        }
        this.actualFinalOutputs.merge(actualKey, amount, BigInteger::add);
        markDurableMutation();
    }

    /**
     * @return total actual variants already owned for final delivery
     */
    public BigInteger actualFinalOutputAmount() {
        return this.actualFinalOutputs.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
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
    public void completeVirtually(BigInteger amount) {
        if (!productionComplete() || this.failed || this.completionSealed ||
                !this.actualFinalOutputs.isEmpty() || !amount.equals(this.deliveryRemaining)) {
            throw new IllegalStateException(
                    "A Trinity virtual completion requires the exact remaining target after production completes");
        }
        this.completionSealed = true;
        this.completionBuffer = BigInteger.ZERO;
        this.deliveryRemaining = BigInteger.ZERO;
        markDurableMutation();
    }

    /**
     * @return immutable offer from the isolated completion buffer
     */
    public Optional<GenericStack> completionOffer() {
        if (!this.completionSealed || this.completionBuffer.signum() == 0) {
            return Optional.empty();
        }
        for (var entry : this.actualFinalOutputs.object2ObjectEntrySet()) {
            return Optional.of(new GenericStack(entry.getKey(), TrinityAe2AmountProjection.toAe2Amount(entry.getValue())));
        }
        return Optional.of(new GenericStack(this.targetKey, TrinityAe2AmountProjection.toAe2Amount(this.completionBuffer)));
    }

    /**
     * Deducts only the amount actually accepted by the requester.
     *
     * @param acceptedAmount positive amount accepted from the completion offer
     */
    public void recordDelivered(AEKey acceptedKey, long acceptedAmount) {
        BigInteger delivered = BigInteger.valueOf(acceptedAmount);
        if (!this.completionSealed || acceptedAmount <= 0L || delivered.compareTo(this.completionBuffer) > 0 ||
                delivered.compareTo(this.deliveryRemaining) > 0) {
            throw new IllegalArgumentException("A Trinity delivery must deduct a positive accepted completion amount");
        }
        if (this.actualFinalOutputs.containsKey(acceptedKey)) {
            BigInteger actualAmount = this.actualFinalOutputs.get(acceptedKey);
            if (delivered.compareTo(actualAmount) > 0) {
                throw new IllegalArgumentException("A Trinity delivery exceeds its actual final-output variant");
            }
            if (delivered.equals(actualAmount)) {
                this.actualFinalOutputs.remove(acceptedKey);
            } else {
                this.actualFinalOutputs.put(acceptedKey, actualAmount.subtract(delivered));
            }
        } else if (!this.targetKey.equals(acceptedKey) || delivered.compareTo(
                this.completionBuffer.subtract(actualFinalOutputAmount())) > 0) {
                    throw new IllegalArgumentException("A Trinity delivery key is absent from its completion buffer");
                }
        this.completionBuffer = this.completionBuffer.subtract(delivered);
        this.deliveryRemaining = this.deliveryRemaining.subtract(delivered);
        markDurableMutation();
    }

    /**
     * Transfers all remaining sealed output to the standalone recovery path exactly once.
     *
     * @return released stack, or empty when the buffer contains nothing
     */
    public Map<AEKey, BigInteger> releaseCompletionForStandalone() {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> released = new Object2ObjectLinkedOpenHashMap<>(this.actualFinalOutputs);
        BigInteger actualAmount = actualFinalOutputAmount();
        if (this.completionSealed) {
            BigInteger exactAmount = this.completionBuffer.subtract(actualAmount);
            if (exactAmount.signum() > 0) {
                released.merge(this.targetKey, exactAmount, BigInteger::add);
            }
            this.deliveryRemaining = this.deliveryRemaining.subtract(this.completionBuffer);
            this.completionBuffer = BigInteger.ZERO;
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
    public BigInteger completionAmount(AEKey key) {
        if (!this.completionSealed) {
            return this.actualFinalOutputs.getOrDefault(key, BigInteger.ZERO);
        }
        BigInteger actual = this.actualFinalOutputs.getOrDefault(key, BigInteger.ZERO);
        if (this.targetKey.equals(key)) {
            return actual.add(this.completionBuffer.subtract(actualFinalOutputAmount()));
        }
        return actual;
    }

    /**
     * @return immutable keyed contents currently isolated from ordinary working inventory
     */
    public Map<AEKey, BigInteger> completionContents() {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> contents = new Object2ObjectLinkedOpenHashMap<>(this.actualFinalOutputs);
        if (this.completionSealed) {
            BigInteger exactAmount = this.completionBuffer.subtract(actualFinalOutputAmount());
            if (exactAmount.signum() > 0) {
                contents.merge(this.targetKey, exactAmount, BigInteger::add);
            }
        }
        return Collections.unmodifiableMap(contents);
    }

    /**
     * @return exact requested target amount not yet delivered or released
     */
    public BigInteger deliveryRemaining() {
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

    /** Returns the persisted logical item domains used by stage balances and runtime input selection. */
    public TrinitySameItemPolicy sameItemPolicy() {
        return this.sameItemPolicy;
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
        ObjectArrayList<Stage> stageSnapshots = new ObjectArrayList<>();
        for (int stageIndex : this.stageOrder) {
            stageSnapshots.add(requireStage(stageIndex).snapshot());
        }
        ObjectArrayList<RepeatBlock> repeatSnapshots = new ObjectArrayList<>();
        this.repeatBlocks.values().forEach(repeat -> repeatSnapshots.add(repeat.snapshot()));
        return new TrinityExecutionSnapshot(
                this.catalogRevision,
                this.quantityMode,
                this.sameItemPolicy,
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
        this.sameItemPolicy = plan.sameItemPolicy();
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
            for (int stageIndex : repeat.stageOrder) {
                if (this.repeatByStage.putIfAbsent(stageIndex, repeat) != null) {
                    throw new IllegalArgumentException("A Trinity execution cycle stage belongs to multiple repeats");
                }
            }
        }
        this.seedReserve.putAll(plan.minimumSeed());
        validateInstalledPlan();
    }

    private void adoptInstalledPlan(TrinityPlanExecution prepared) {
        this.catalogRevision = prepared.catalogRevision;
        this.quantityMode = prepared.quantityMode;
        this.sameItemPolicy = prepared.sameItemPolicy;
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
        if (this.catalogRevision < 0L ||
                this.stageOrder.size() != this.stages.size() ||
                !new IntOpenHashSet(this.stageOrder).equals(this.stages.keySet()) ||
                !this.sameItemPolicy.normalizeKey(this.targetKey).equals(this.targetKey) ||
                !normalizedKeys(this.seedReserve.keySet())) {
            throw new IllegalArgumentException("A Trinity execution requires complete plan metadata and stage order");
        }
        for (StageState stage : this.stages.values()) {
            if (!this.stages.keySet().containsAll(stage.dependencies) ||
                    stage.cycle != this.repeatByStage.containsKey(stage.index) ||
                    !normalizedKeys(stage.requiredAtStart.keySet()) ||
                    !normalizedKeys(stage.netChange.keySet()) ||
                    !normalizedKeys(stage.inputKeys) ||
                    !normalizedKeys(stage.waitingKeys)) {
                throw new IllegalArgumentException("A Trinity execution plan has inconsistent dependencies or cycle membership");
            }
        }
    }

    private boolean normalizedKeys(Iterable<AEKey> keys) {
        for (AEKey key : keys) {
            if (!this.sameItemPolicy.normalizeKey(key).equals(key)) {
                return false;
            }
        }
        return true;
    }

    private void validateRestoredCursors() {
        for (StageState stage : this.stages.values()) {
            if (stage.completed) {
                if (stage.currentFiring != stage.firings.size() ||
                        stage.firings.stream().anyMatch(firing -> firing.remainingCount.signum() != 0) ||
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
                if (firingIndex < stage.currentFiring && firing.remainingCount.signum() != 0) {
                    throw new IllegalArgumentException("A Trinity stage retained work before its current firing");
                }
                if (firingIndex > stage.currentFiring && firing.initialized) {
                    throw new IllegalArgumentException("A Trinity stage initialized a firing after its current cursor");
                }
            }
            validateWait(stage);
            if (!stage.cycle) {
                FiringState current = stage.firings.get(stage.currentFiring);
                if (!current.initialized || current.remainingCount.signum() <= 0 ||
                        current.remainingCount.compareTo(current.plannedCount) > 0) {
                    throw new IllegalArgumentException("An unfinished Trinity DAG stage requires positive remaining work");
                }
            }
        }

        IntOpenHashSet cycleStages = new IntOpenHashSet();
        for (RepeatState repeat : this.repeatBlocks.values()) {
            if (!cycleStages.addAll(repeat.stageOrder)) {
                throw new IllegalArgumentException("A Trinity cycle stage appears in multiple restored repeat blocks");
            }
            if (repeat.remainingRepetitions.signum() == 0) {
                if (repeat.waveCount.signum() != 0 ||
                        repeat.stageOrder.intStream().anyMatch(index -> !requireStage(index).completed)) {
                    throw new IllegalArgumentException("A finished Trinity repeat block requires completed stages and no wave");
                }
                continue;
            }
            if (repeat.stageOrder.intStream().anyMatch(index -> requireStage(index).completed)) {
                throw new IllegalArgumentException("An unfinished Trinity repeat block cannot contain completed stages");
            }
            if (repeat.waveCount.signum() == 0 && repeat.cursor != 0) {
                throw new IllegalArgumentException("A Trinity repeat block without a wave must point at its first stage");
            }
            for (int position = 0; position < repeat.stageOrder.size(); position++) {
                StageState stage = requireStage(repeat.stageOrder.getInt(position));
                if (position < repeat.cursor &&
                        (stage.currentFiring != stage.firings.size() ||
                                stage.firings.stream().anyMatch(firing -> !firing.initialized))) {
                    throw new IllegalArgumentException("A Trinity repeat stage before the cursor must finish every wave firing");
                }
                if (position > repeat.cursor && stage.firings.stream().anyMatch(firing -> firing.initialized)) {
                    throw new IllegalArgumentException("A Trinity repeat stage after the cursor cannot start its wave");
                }
            }
            StageState active = requireStage(repeat.stageOrder.getInt(repeat.cursor));
            if (active.currentFiring >= active.firings.size()) {
                throw new IllegalArgumentException("A Trinity active repeat stage requires a current firing");
            }
            FiringState current = active.firings.get(active.currentFiring);
            if (repeat.waveCount.signum() == 0) {
                if (active.currentFiring != 0 || current.initialized) {
                    throw new IllegalArgumentException("A Trinity new wave must begin with an uninitialized first firing");
                }
            } else {
                BigInteger maximumRemaining = current.plannedCount.multiply(repeat.waveCount);
                if (current.initialized &&
                        (current.remainingCount.signum() <= 0 ||
                                current.remainingCount.compareTo(maximumRemaining) > 0)) {
                    throw new IllegalArgumentException("A Trinity active wave requires bounded initialized firing work");
                }
            }
        }
    }

    private void validateCompletionState() {
        if (this.completionBuffer.signum() < 0 || this.deliveryRemaining.signum() < 0 ||
                this.deliveryRemaining.compareTo(this.targetAmount) > 0 || this.completionBuffer.compareTo(this.deliveryRemaining) > 0) {
            throw new IllegalArgumentException("A Trinity completion buffer contains impossible delivery amounts");
        }
        AEItemKey targetItem = this.targetKey instanceof AEItemKey itemKey ? itemKey : null;
        if (targetItem == null && !this.actualFinalOutputs.isEmpty()) {
            throw new IllegalArgumentException("Only item targets can retain actual final-output variants");
        }
        BigInteger actualAmount = BigInteger.ZERO;
        if (targetItem != null) {
            for (var entry : this.actualFinalOutputs.object2ObjectEntrySet()) {
                if (!(entry.getKey() instanceof AEItemKey itemKey) || entry.getValue().signum() <= 0 ||
                        itemKey.getItem() != targetItem.getItem()) {
                    throw new IllegalArgumentException(
                            "A Trinity actual final-output buffer contains an invalid item variant");
                }
                actualAmount = actualAmount.add(entry.getValue());
            }
        }
        if (actualAmount.compareTo(this.deliveryRemaining) > 0 || this.completionSealed && actualAmount.compareTo(this.completionBuffer) > 0) {
            throw new IllegalArgumentException("A Trinity actual final-output buffer exceeds remaining delivery ownership");
        }
        if (!this.completionSealed &&
                (this.completionBuffer.signum() != 0 || !this.deliveryRemaining.equals(this.targetAmount))) {
            throw new IllegalArgumentException("An unsealed Trinity completion buffer cannot record delivery progress");
        }
        if (this.completionSealed && !productionComplete()) {
            throw new IllegalArgumentException("A Trinity completion buffer cannot be sealed before production completes");
        }
        if (this.completionSealed && !this.completionBuffer.equals(this.deliveryRemaining)) {
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
        this.leasedWorks.clear();
        this.readyQueue.clear();
        this.inputStageIndex.clear();
        this.dependents.clear();
        this.retryQueue.clear();
        for (StageState stage : this.stages.values()) {
            stage.leased = false;
            for (int dependency : stage.dependencies) {
                this.dependents.computeIfAbsent(dependency, ignored -> new IntLinkedOpenHashSet()).add(stage.index);
            }
            ObjectLinkedOpenHashSet<AEKey> indexedKeys = new ObjectLinkedOpenHashSet<>(stage.inputKeys);
            indexedKeys.addAll(stage.waitingKeys);
            for (AEKey key : indexedKeys) {
                this.inputStageIndex.computeIfAbsent(key, ignored -> new IntLinkedOpenHashSet()).add(stage.index);
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
            for (int stageIndex : this.stageOrder) {
                enqueueIfEligible(requireStage(stageIndex));
            }
        }
    }

    /**
     * Recomputes derived material-flow dependencies recorded by older plans.
     *
     * <p>
     * Persisted proposals and leases are transient, so restoring a job is the safe point to remove obsolete global
     * cycle, shared-output, reverse-flow and same-pattern barriers. A shared input remains ordered because an older
     * snapshot lacks quantity provenance: a later permanent consumer must not steal a scarce seed before an earlier
     * cycle returns it. Positive net production consumed by a later stage also remains ordered. Repeat cursors
     * serialize one cycle internally.
     * </p>
     *
     * @return whether at least one persisted dependency set changed
     */
    private boolean recomputeRestoredDependencies() {
        Int2ObjectLinkedOpenHashMap<ExecutionFootprint> footprints = new Int2ObjectLinkedOpenHashMap<>();
        for (int stageIndex : this.stageOrder) {
            StageState stage = requireStage(stageIndex);
            RepeatState repeat = this.repeatByStage.get(stageIndex);
            footprints.put(
                    stageIndex,
                    repeat == null ?
                            ExecutionFootprint.fromStage(stage) :
                            ExecutionFootprint.fromRepeat(repeat, this.stages));
        }
        boolean changed = false;
        for (int stageIndex : this.stageOrder) {
            StageState stage = requireStage(stageIndex);
            IntLinkedOpenHashSet original = new IntLinkedOpenHashSet(stage.dependencies);
            IntLinkedOpenHashSet recomputed = new IntLinkedOpenHashSet();
            ExecutionFootprint current = footprints.get(stageIndex);
            for (int dependency : original) {
                ExecutionFootprint candidate = footprints.get(dependency);
                if (candidate == null) {
                    throw new IllegalArgumentException("A restored Trinity dependency is absent from its stage order");
                }
                if (candidate.requiresOrderingBefore(current)) {
                    recomputed.add(dependency);
                }
            }
            if (!original.equals(recomputed)) {
                stage.dependencies.clear();
                stage.dependencies.addAll(recomputed);
                changed = true;
            }
        }
        return changed;
    }

    private Work createWork(StageState stage) {
        initializeCurrentFiring(stage);
        FiringState firing = stage.firings.get(stage.currentFiring);
        BigInteger maximum = firing.remainingCount;
        if (stage.cycle) {
            RepeatState repeat = requireRepeat(stage.index);
            if (repeat.waveCount.signum() == 0) {
                if (repeat.cursor != 0 || stage.currentFiring != 0 || firing.initialized) {
                    throw new IllegalStateException("A Trinity cycle has an invalid uninitialized wave cursor");
                }
                maximum = firing.plannedCount.multiply(repeat.remainingRepetitions);
            }
        }
        return new Work(
                this.generation,
                stage.index,
                stage.currentFiring,
                firing.patternIdentity,
                firing.primaryOutput,
                firing.variantOrdinal,
                physicalWindow(maximum),
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
        if (repeat.waveCount.signum() > 0) {
            firing.remainingCount = firing.plannedCount.multiply(repeat.waveCount);
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
        int expectedStage = repeat.stageOrder.getInt(repeat.cursor);
        if (expectedStage != stage.index || repeat.waveCount.signum() <= 0) {
            throw new IllegalStateException("A Trinity cycle stage completed outside its active wave cursor");
        }
        if (repeat.cursor + 1 < repeat.stageOrder.size()) {
            repeat.cursor++;
            StageState next = requireStage(repeat.stageOrder.getInt(repeat.cursor));
            next.resetForCycleWave();
            enqueueIfEligible(next);
            return;
        }

        repeat.remainingRepetitions = repeat.remainingRepetitions.subtract(repeat.waveCount);
        if (repeat.remainingRepetitions.signum() < 0) {
            throw new IllegalStateException("A Trinity cycle wave exceeded its remaining repetitions");
        }
        if (repeat.remainingRepetitions.signum() == 0) {
            for (int stageIndex : repeat.stageOrder) {
                StageState completed = requireStage(stageIndex);
                completed.completed = true;
                enqueueDependents(stageIndex);
            }
            repeat.waveCount = BigInteger.ZERO;
            return;
        }

        repeat.cursor = 0;
        repeat.waveCount = BigInteger.ZERO;
        for (int stageIndex : repeat.stageOrder) {
            requireStage(stageIndex).resetForCycleWave();
        }
        enqueueIfEligible(requireStage(repeat.stageOrder.getFirst()));
    }

    private void enqueueDependents(int stageIndex) {
        IntSet affected = this.dependents.get(stageIndex);
        if (affected == null) {
            return;
        }
        for (int dependent : affected) {
            enqueueIfEligible(requireStage(dependent));
        }
    }

    private void enqueueIfEligible(StageState stage) {
        if (eligible(stage)) {
            this.readyQueue.add(stage.index);
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
        return repeat.remainingRepetitions.signum() > 0 && repeat.stageOrder.getInt(repeat.cursor) == stage.index &&
                dependenciesComplete(stage, repeat);
    }

    private boolean dependenciesComplete(StageState stage, @Nullable RepeatState repeat) {
        for (int dependency : stage.dependencies) {
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
        Work leased = this.leasedWorks.get(work.stageIndex());
        if (leased == null || !leased.equals(work)) {
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
        this.leasedWorks.remove(stage.index);
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
        this.retryQueue.enqueue(new RetryEntry(stage.retryAt, stage.index, stage.retryVersion, stage.waitKind));
    }

    private void activateDueRetries(long currentTick) {
        boolean changed = false;
        while (!this.retryQueue.isEmpty() && this.retryQueue.first().retryAt <= currentTick) {
            RetryEntry retry = this.retryQueue.dequeue();
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

    private Set<AEKey> normalizeObservedKeys(Set<AEKey> keys) {
        ObjectLinkedOpenHashSet<AEKey> normalized = new ObjectLinkedOpenHashSet<>();
        keys.forEach(key -> normalized.add(this.sameItemPolicy.normalizeKey(key)));
        return Collections.unmodifiableSet(normalized);
    }

    private Set<AEKey> normalizeNonEmptyObservedKeys(Set<AEKey> keys, String role) {
        Set<AEKey> normalized = normalizeObservedKeys(keys);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("A Trinity " + role + " requires at least one key");
        }
        return normalized;
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
        if (retryAt <= savedAtTick) {
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

    private static BigInteger ceilDiv(BigInteger amount, BigInteger divisor) {
        BigInteger[] divided = amount.divideAndRemainder(divisor);
        return divided[1].signum() == 0 ? divided[0] : divided[0].add(BigInteger.ONE);
    }

    private static long physicalWindow(BigInteger amount) {
        return amount.min(MAX_PHYSICAL_AMOUNT).longValueExact();
    }

    /**
     * Rebuilds the exact one-wave seed lower bound from durable stage balances.
     *
     * <p>
     * Repeat-block NBT predates a dedicated seed field. Keeping this reconstruction here preserves every existing
     * schema while retaining the resource contract required to start a newly restored cycle wave.
     * </p>
     */
    private static Map<AEKey, BigInteger> reconstructMinimumSeed(List<Integer> stageOrder,
                                                                 Int2ObjectMap<StageState> stages) {
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> seed = new Object2ObjectLinkedOpenHashMap<>();
        Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> balances = new Object2ObjectLinkedOpenHashMap<>();
        for (int stageIndex : stageOrder) {
            StageState stage = stages.get(stageIndex);
            if (stage == null || !stage.cycle) {
                throw new IllegalArgumentException("A Trinity repeat block references an invalid cycle stage");
            }
            for (Map.Entry<AEKey, BigInteger> required : stage.requiredAtStart.entrySet()) {
                BigInteger balance = balances.getOrDefault(required.getKey(), BigInteger.ZERO);
                if (balance.compareTo(required.getValue()) >= 0) {
                    continue;
                }
                BigInteger deficit = required.getValue().subtract(balance);
                seed.merge(required.getKey(), deficit, BigInteger::add);
                balances.put(required.getKey(), balance.add(deficit));
            }
            for (Map.Entry<AEKey, BigInteger> change : stage.netChange.entrySet()) {
                BigInteger updated = balances.getOrDefault(change.getKey(), BigInteger.ZERO).add(change.getValue());
                if (updated.signum() < 0) {
                    throw new IllegalArgumentException("A Trinity repeat block has an impossible negative stage balance");
                }
                if (updated.signum() == 0) {
                    balances.remove(change.getKey());
                } else {
                    balances.put(change.getKey(), updated);
                }
            }
        }
        return Collections.unmodifiableMap(seed);
    }

    private record RetryEntry(long retryAt, int stageIndex, long version, WaitKind waitKind) {}

    private record ExecutionFootprint(
                                      Set<AEKey> inputs,
                                      Set<AEKey> positiveNetOutputs) {

        private static ExecutionFootprint fromStage(StageState stage) {
            ObjectLinkedOpenHashSet<AEKey> inputs = new ObjectLinkedOpenHashSet<>(stage.requiredAtStart.keySet());
            inputs.addAll(stage.inputKeys);
            ObjectLinkedOpenHashSet<AEKey> positiveNetOutputs = new ObjectLinkedOpenHashSet<>();
            stage.netChange.forEach((key, amount) -> {
                if (amount.signum() > 0) {
                    positiveNetOutputs.add(key);
                }
            });
            return new ExecutionFootprint(
                    Set.copyOf(inputs),
                    Set.copyOf(positiveNetOutputs));
        }

        private static ExecutionFootprint fromRepeat(
                                                     RepeatState repeat,
                                                     Int2ObjectMap<StageState> stages) {
            ObjectLinkedOpenHashSet<AEKey> inputs = new ObjectLinkedOpenHashSet<>(repeat.minimumSeed.keySet());
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> blockNetChange = new Object2ObjectLinkedOpenHashMap<>();
            for (int memberIndex : repeat.stageOrder) {
                StageState member = stages.get(memberIndex);
                if (member == null) {
                    throw new IllegalArgumentException("A restored Trinity repeat references an absent stage");
                }
                inputs.addAll(member.inputKeys);
                member.netChange.forEach((key, amount) -> blockNetChange.merge(key, amount, BigInteger::add));
            }
            ObjectLinkedOpenHashSet<AEKey> positiveNetOutputs = new ObjectLinkedOpenHashSet<>();
            blockNetChange.forEach((key, amount) -> {
                if (amount.signum() < 0) {
                    inputs.add(key);
                } else if (amount.signum() > 0) {
                    positiveNetOutputs.add(key);
                }
            });
            return new ExecutionFootprint(
                    Set.copyOf(inputs),
                    Set.copyOf(positiveNetOutputs));
        }

        private boolean requiresOrderingBefore(ExecutionFootprint stage) {
            return intersects(this.inputs, stage.inputs) ||
                    intersects(this.positiveNetOutputs, stage.inputs);
        }

        private static boolean intersects(Set<AEKey> left, Set<AEKey> right) {
            for (AEKey key : left) {
                if (right.contains(key)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class FiringState {

        private final TrinityPatternIdentity patternIdentity;
        private final AEKey primaryOutput;
        private final int variantOrdinal;
        private final BigInteger plannedCount;
        private final Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> outputs;
        private BigInteger remainingCount;
        private boolean initialized;

        private FiringState(TrinityPatternIdentity patternIdentity,
                            AEKey primaryOutput,
                            int variantOrdinal,
                            BigInteger plannedCount,
                            Map<AEKey, BigInteger> outputs,
                            BigInteger remainingCount,
                            boolean initialized) {
            if (variantOrdinal < 0 || plannedCount.signum() <= 0 || remainingCount.signum() < 0 ||
                    (!initialized && remainingCount.signum() != 0)) {
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
            BigInteger count = firing.count();
            return new FiringState(
                    firing.patternIdentity(),
                    firing.primaryOutput(),
                    firing.variantOrdinal(),
                    count,
                    firing.outputs(),
                    cycle ? BigInteger.ZERO : count,
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

        private static Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copyOutputs(Map<AEKey, BigInteger> source) {
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> outputs = new Object2ObjectLinkedOpenHashMap<>();
            source.forEach((key, amount) -> {
                if (amount.signum() <= 0) {
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
        private final IntLinkedOpenHashSet dependencies;
        private final ObjectArrayList<FiringState> firings;
        private final Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> requiredAtStart;
        private final Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> netChange;
        private final ObjectLinkedOpenHashSet<AEKey> inputKeys = new ObjectLinkedOpenHashSet<>();
        private final ObjectLinkedOpenHashSet<AEKey> waitingKeys = new ObjectLinkedOpenHashSet<>();
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
                           Map<AEKey, BigInteger> requiredAtStart,
                           Map<AEKey, BigInteger> netChange) {
            if (index < 0 || firings.isEmpty()) {
                throw new IllegalArgumentException("A Trinity stage state requires index, dependencies and firings");
            }
            this.index = index;
            this.cycle = cycle;
            this.dependencies = new IntLinkedOpenHashSet(dependencies);
            this.firings = new ObjectArrayList<>(firings);
            this.requiredAtStart = copyAmounts(requiredAtStart, false, "stage start requirement");
            this.netChange = copyAmounts(netChange, true, "stage net change");
        }

        private static StageState fromPlan(TrinityPlanStage stage) {
            ObjectArrayList<FiringState> firings = new ObjectArrayList<>();
            stage.firings().forEach(firing -> firings.add(FiringState.fromPlan(firing, stage.cycleStage())));
            return new StageState(
                    stage.index(),
                    stage.cycleStage(),
                    stage.dependencies(),
                    firings,
                    stage.requiredAtStart(),
                    stage.netChange());
        }

        private static StageState fromSnapshot(Stage snapshot, long savedAtTick, long currentTick) {
            ObjectArrayList<FiringState> firings = new ObjectArrayList<>();
            snapshot.firings().forEach(firing -> firings.add(FiringState.fromSnapshot(firing)));
            StageState restored = new StageState(
                    snapshot.index(),
                    snapshot.cycle(),
                    new IntLinkedOpenHashSet(snapshot.dependencies()),
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
                firing.remainingCount = BigInteger.ZERO;
                firing.initialized = false;
            }
        }

        private Stage snapshot() {
            ObjectArrayList<Firing> firingSnapshots = new ObjectArrayList<>();
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

        private static Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copyAmounts(Map<AEKey, BigInteger> source,
                                                                                     boolean signed,
                                                                                     String role) {
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
            source.forEach((key, amount) -> {
                if (signed ? amount.signum() == 0 : amount.signum() <= 0) {
                    throw new IllegalArgumentException("A Trinity " + role + " contains an invalid amount");
                }
                copied.put(key, amount);
            });
            return copied;
        }
    }

    private static final class RepeatState {

        private final int index;
        private final IntArrayList stageOrder;
        private final Map<AEKey, BigInteger> minimumSeed;
        private BigInteger remainingRepetitions;
        private int cursor;
        private BigInteger waveCount;

        private RepeatState(int index,
                            List<Integer> stageOrder,
                            Map<AEKey, BigInteger> minimumSeed,
                            BigInteger remainingRepetitions,
                            int cursor,
                            BigInteger waveCount) {
            if (index < 0 || stageOrder.isEmpty() || remainingRepetitions.signum() < 0 ||
                    cursor < 0 || cursor >= stageOrder.size() || waveCount.signum() < 0 ||
                    waveCount.compareTo(remainingRepetitions) > 0) {
                throw new IllegalArgumentException("A Trinity repeat state contains an invalid cursor or count");
            }
            IntOpenHashSet uniqueStages = new IntOpenHashSet();
            for (int stageIndex : stageOrder) {
                if (stageIndex < 0 || !uniqueStages.add(stageIndex)) {
                    throw new IllegalArgumentException("A Trinity repeat state requires unique non-negative stages");
                }
            }
            this.index = index;
            this.stageOrder = new IntArrayList(stageOrder);
            this.minimumSeed = copyMinimumSeed(minimumSeed);
            this.remainingRepetitions = remainingRepetitions;
            this.cursor = cursor;
            this.waveCount = waveCount;
        }

        private static RepeatState fromPlan(TrinityCycleRepeatBlock block) {
            return new RepeatState(
                    block.index(),
                    block.stageOrder(),
                    block.minimumSeed(),
                    block.repetitions(),
                    0,
                    BigInteger.ZERO);
        }

        private static RepeatState fromSnapshot(RepeatBlock snapshot, Int2ObjectMap<StageState> stages) {
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

        private static Map<AEKey, BigInteger> copyMinimumSeed(Map<AEKey, BigInteger> source) {
            Object2ObjectLinkedOpenHashMap<AEKey, BigInteger> copied = new Object2ObjectLinkedOpenHashMap<>();
            source.forEach((key, amount) -> {
                if (amount.signum() <= 0) {
                    throw new IllegalArgumentException("A Trinity repeat minimum seed must be positive");
                }
                copied.put(key, amount);
            });
            return Collections.unmodifiableMap(copied);
        }
    }
}
