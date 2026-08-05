package com.fish_dan_.data_energistics.common.crafting.trinity.execution.state;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * Event-driven, persistence-safe execution cursor for a compact Trinity crafting plan.
 */
public interface TrinityPlanExecution {

    /**
     * Observable execution states used by the CPU scheduler and status UI.
     */
    enum Status {
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
    record Work(long generation,
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
    record CycleWaveLimit(long maximumLogicalFirings, Set<AEKey> observedKeys) {

        /** Validates the non-negative limit and isolates the wake-key set from callers. */
        public CycleWaveLimit {
            if (maximumLogicalFirings < 0L || observedKeys == null) {
                throw new IllegalArgumentException("A Trinity cycle wave limit requires a non-negative count and key set");
            }
            observedKeys = Set.copyOf(observedKeys);
        }
    }

    /**
     * Creates a fresh execution cursor from a fully validated compact plan.
     *
     * @param plan        executable Trinity plan
     * @param currentTick current server tick
     * @return initialized execution state
     */
    static TrinityPlanExecution create(TrinityCraftingPlan plan, long currentTick) {
        return TrinityPlanExecutionImpl.create(plan, currentTick);
    }

    /**
     * Restores a supported compact execution snapshot and deterministically rebuilds its transient queues and indexes.
     *
     * @param tag         encoded execution state
     * @param registries  server registry lookup used by AE key codecs
     * @param currentTick current server tick used to release due retries
     * @return restored execution state
     */
    static TrinityPlanExecution restore(CompoundTag tag,
                                        HolderLookup.Provider registries,
                                        long currentTick) {
        return TrinityPlanExecutionImpl.restore(tag, registries, currentTick);
    }

    /**
     * @return current scheduler-visible state
     */
    Status status();

    /**
     * Returns the earliest deterministic provider, dynamic-input or budget retry tick.
     *
     * @return retry tick, or empty when the current status is not time-gated
     */
    OptionalLong nextRetryTick();

    /**
     * @return catalog revision against which current remaining stages were planned
     */
    long catalogRevision();

    /**
     * @return requested delivery quantity semantics
     */
    CraftingQuantityMode quantityMode();

    /**
     * @return immutable requested delivery stack
     */
    GenericStack finalOutput();

    /**
     * Returns the current leased work, or dequeues one eligible stage without scanning unrelated stages.
     *
     * @param currentTick current server tick used to activate due retries
     * @return current dispatch offer, if any
     */
    Optional<Work> poll(long currentTick);

    /**
     * @return number of unique stage indexes currently held in the transient ready queue
     */
    int queuedStageCount();

    /**
     * Registers material keys whose changes can wake one stage; registrations are persisted, while the reverse index is
     * not.
     *
     * @param stageIndex stable stage index
     * @param keys       legal material keys relevant to that stage
     */
    void registerInputKeys(int stageIndex, Set<AEKey> keys);

    /**
     * Wakes only stages indexed by the changed material key.
     *
     * @param key changed storage key
     * @return whether at least one waiting stage was released
     */
    boolean wake(AEKey key);

    /**
     * Defers current work until one of its required material keys changes.
     *
     * @param work current leased work
     * @param keys material keys that can satisfy the wait
     */
    void deferInput(Work work, Set<AEKey> keys);

    /**
     * Defers current cyclic work with event wakeup plus exponential tick backoff.
     *
     * @param work          current leased work
     * @param keys          keys that may make a dynamic route feasible
     * @param currentTick   current server tick
     * @param maxRetryTicks inclusive retry delay cap
     */
    void deferDynamicInput(Work work, Set<AEKey> keys, long currentTick, int maxRetryTicks);

    /**
     * Defers current work with provider-specific exponential tick backoff.
     *
     * @param work          current leased work
     * @param currentTick   current server tick
     * @param maxRetryTicks inclusive retry delay cap
     */
    void deferProvider(Work work, long currentTick, int maxRetryTicks);

    /**
     * Suspends dispatch while the caller replans the complete remaining request.
     *
     * @param work current leased work whose pattern or route became invalid
     */
    void markPlanning(Work work);

    /**
     * Replaces all remaining stage cursors after a successful replan while retaining ledger and completion ownership.
     *
     * @param replacement replacement plan for the remaining request
     * @param currentTick current server tick
     */
    void replaceRemainingPlan(TrinityCraftingPlan replacement, long currentTick);

    /**
     * Records the exact count accepted by a provider and advances only that firing cursor.
     *
     * @param work          current leased work
     * @param acceptedCount positive accepted logical firing count
     */
    void recordAccepted(Work work, long acceptedCount);

    /**
     * Releases current work and defers it until the next server tick.
     *
     * @param work        current leased work
     * @param currentTick current server tick at budget exhaustion
     */
    void markBudgetExhausted(Work work, long currentTick);

    /**
     * Determines the strict terminal deadlock condition without mutating or refunding state.
     *
     * @param hasInFlight whether a provider still owns unfinished output
     * @return true only when remaining work has no ready, retry, wait, planning, budget or in-flight path
     */
    boolean deadlocked(boolean hasInFlight);

    /**
     * Terminates remaining execution with a non-empty diagnostic reason.
     *
     * @param reason structured failure reason suitable for logging and persistence
     */
    void fail(String reason);

    /**
     * @return failure reason when status is {@link Status#FAILED}
     */
    Optional<String> failureReason();

    /**
     * @return immutable exact seed reserve captured from the current plan
     */
    Map<AEKey, Long> seedReserve();

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
    CycleWaveLimit maximumCycleLogicalFirings(Work work, ToLongFunction<AEKey> availableAmount);

    /**
     * Reconstructs the exact pattern-declared outputs of every undispatched firing without expanding cycle repeats.
     * Legacy schema 2/3 firings that predate output metadata are omitted instead of reporting invented values.
     *
     * @return immutable pending-output projection for AE2's crafting CPU status table
     */
    Map<AEKey, Long> pendingOutputs();

    /**
     * @return ownership ledger shared by dynamic material transactions
     */
    TrinityBorrowingLedger borrowingLedger();

    /**
     * @return whether every planned stage and repeat block has finished
     */
    boolean productionComplete();

    /**
     * Isolates the exact target delivery amount from the working inventory after production and in-flight completion.
     *
     * @param amount exact amount moved into the completion buffer
     */
    void sealCompletion(long amount);

    /**
     * @return immutable offer from the isolated completion buffer
     */
    Optional<GenericStack> completionOffer();

    /**
     * Deducts only the amount actually accepted by the requester.
     *
     * @param acceptedAmount positive amount accepted from the completion offer
     */
    void recordDelivered(long acceptedAmount);

    /**
     * Transfers all remaining sealed output to the standalone recovery path exactly once.
     *
     * @return released stack, or empty when the buffer contains nothing
     */
    Optional<GenericStack> releaseCompletionForStandalone();

    /**
     * @return exact requested target amount not yet delivered or released
     */
    long deliveryRemaining();

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
    long durableRevision();

    /**
     * Encodes durable state without serializing transient queues or indexes.
     *
     * @param registries  server registry lookup used by AE key codecs
     * @param currentTick current server tick used to persist relative retry delays
     * @return strict execution NBT
     */
    CompoundTag save(HolderLookup.Provider registries, long currentTick);
}
