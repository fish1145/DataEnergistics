package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;

import java.util.function.Supplier;

/**
 * Independent bounded executor for pure dispatch-proposal calculations.
 *
 * <p>
 * Submission never mutates crafting resources. A rejected submission leaves no worker or grid permit behind, allowing
 * the caller to retry or use the synchronous Phase 3 path.
 * </p>
 */
public interface DispatchProposalScheduler extends AutoCloseable {

    /**
     * @return scheduler using the architecture hard limits
     */
    static DispatchProposalScheduler create(Supplier<TrinityComputationCache> computationCache) {
        return create(DispatchProposalLimits.defaults(), computationCache);
    }

    /**
     * Creates an independent bounded scheduler.
     *
     * @param limits immutable executor limits
     * @return running scheduler
     */
    static DispatchProposalScheduler create(DispatchProposalLimits limits,
                                            Supplier<TrinityComputationCache> computationCache) {
        return new BoundedDispatchProposalScheduler(limits, computationCache);
    }

    /**
     * Attempts to admit one worker proposal.
     *
     * @param request immutable server-thread snapshot
     * @param wakeup  non-blocking callback that only enqueues the owning worker after completion
     * @param policy  per-grid Governor admission policy captured for this attempt
     * @return accepted ticket or an explicit rejection that owns no resources
     */
    Submission submit(CraftingDispatchProposalRequest request, Runnable wakeup, DispatchProposalPolicy policy);

    /**
     * Retains the pre-Governor contract with deterministic hard limits.
     */
    default Submission submit(CraftingDispatchProposalRequest request, Runnable wakeup) {
        return submit(request, wakeup, DispatchProposalPolicy.defaults());
    }

    /**
     * Drains accumulated proposal timing and admission facts for one process-local grid generation.
     *
     * <p>
     * Current queue depth and outstanding counts are snapshots and are not reset.
     * </p>
     *
     * @param gridGeneration current grid publication scope
     * @return independent immutable metrics
     */
    DispatchProposalMetrics snapshotAndResetMetrics(long gridGeneration);

    /**
     * Cancels every unconsumed proposal owned by one unloaded Grid and releases its provider reservations.
     *
     * @param gridGeneration unloaded Grid publication scope
     */
    void clearGrid(long gridGeneration);

    /**
     * Cancels all outstanding tickets and stops the independent executor.
     */
    @Override
    void close();

    /**
     * Submission result without exceptions for expected bounded-capacity rejection.
     */
    sealed interface Submission permits Accepted, Rejected {}

    /**
     * @param ticket admitted outstanding ticket
     */
    record Accepted(DispatchProposalTicket ticket) implements Submission {

        public Accepted {
            if (ticket == null) {
                throw new IllegalArgumentException("Accepted dispatch proposal ticket must not be null");
            }
        }
    }

    /**
     * @param reason expected admission rejection
     */
    record Rejected(RejectionReason reason) implements Submission {

        public Rejected {
            if (reason == null) {
                throw new IllegalArgumentException("Dispatch proposal rejection reason must not be null");
            }
        }
    }

    /**
     * Bounded admission reasons used by the server thread to select retry or synchronous fallback.
     */
    enum RejectionReason {
        WORKER_BUSY,
        GRID_LIMIT,
        HIGH_WATER,
        QUEUE_FULL,
        DISABLED,
        CLOSED
    }
}
