package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.runtime;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchLease;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposal;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalPolicy;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalScheduler;

import java.util.function.Supplier;

/**
 * Server-thread owner of the single outstanding proposal permitted for one Trinity worker.
 *
 * <p>
 * The coordinator retains an opaque work object only for server-thread identity comparison. The scheduler receives
 * exclusively the immutable request and never observes this object.
 * </p>
 */
public interface TrinityWorkerProposalCoordinator {

    /**
     * Creates an idle coordinator.
     *
     * @param scheduler running scheduler supplier resolved only when new work is submitted
     * @return independent worker coordinator
     */
    static TrinityWorkerProposalCoordinator create(Supplier<DispatchProposalScheduler> scheduler) {
        return new TrinityWorkerProposalCoordinatorImpl(scheduler);
    }

    /**
     * Polls the existing proposal and cancels it when any lease or server-thread work identity changed.
     *
     * @param currentLease current fully recaptured generation lease
     * @param workIdentity exact current legacy task or compact work object
     * @return non-blocking proposal decision
     */
    Decision poll(CraftingDispatchLease currentLease, Object workIdentity);

    /**
     * Attempts to create the worker's only outstanding proposal.
     *
     * @param request      immutable pure-planning request
     * @param workIdentity exact server-thread work identity retained for later stale validation
     * @param wakeup       callback that enqueues only this worker after completion
     * @param policy       current per-grid Governor proposal policy
     * @return pending or synchronous-fallback decision
     */
    Decision submit(
                    CraftingDispatchProposalRequest request,
                    Object workIdentity,
                    Runnable wakeup,
                    DispatchProposalPolicy policy);

    /**
     * Retains the pre-Governor contract with deterministic hard limits.
     */
    default Decision submit(CraftingDispatchProposalRequest request, Object workIdentity, Runnable wakeup) {
        return submit(request, workIdentity, wakeup, DispatchProposalPolicy.defaults());
    }

    /**
     * @return whether this worker is waiting exclusively for its background proposal completion
     */
    boolean pending();

    /**
     * @return whether this worker still owns any unconsumed proposal ticket state
     */
    boolean outstanding();

    /**
     * Releases the currently consumed ready proposal after server-thread commit or rejection.
     */
    void release();

    /**
     * Records and releases a proposal rejected by server-thread generation or route revalidation.
     */
    void discardStale();

    /**
     * Cancels any outstanding proposal during job, route, reload or worker lifecycle changes.
     */
    void cancel();

    /**
     * Non-blocking server-thread decision.
     */
    sealed interface Decision permits Empty, Pending, Ready, NoCapacity, Deferred, Fallback {}

    /**
     * No proposal exists, so the caller may capture and submit immutable candidates.
     */
    enum Empty implements Decision {
        INSTANCE
    }

    /**
     * The outstanding proposal is queued or calculating.
     */
    enum Pending implements Decision {
        INSTANCE
    }

    /**
     * @param proposal completed immutable proposal awaiting server-thread revalidation
     */
    record Ready(CraftingDispatchProposal proposal) implements Decision {

        public Ready {
            if (proposal == null) {
                throw new IllegalArgumentException("Ready worker dispatch proposal must not be null");
            }
        }
    }

    /**
     * No immutable candidate had a safe positive offer.
     */
    enum NoCapacity implements Decision {
        INSTANCE
    }

    /**
     * @param reason bounded scheduler pressure requiring a retry without synchronous resource mutation
     */
    record Deferred(DeferredReason reason) implements Decision {

        public Deferred {
            if (reason == null) {
                throw new IllegalArgumentException("Worker proposal deferral reason must not be null");
            }
        }
    }

    /**
     * @param reason expected reason to use the Phase 3 synchronous path
     */
    record Fallback(FallbackReason reason) implements Decision {

        public Fallback {
            if (reason == null) {
                throw new IllegalArgumentException("Worker proposal fallback reason must not be null");
            }
        }
    }

    /**
     * Expected proposal failures that never authorize resource mutation in the background.
     */
    enum FallbackReason {
        SCHEDULER_CLOSED,
        SCHEDULER_DISABLED,
        CALCULATION_FAILED,
        CANCELLED
    }

    /**
     * Expected bounded-admission pressure that must not fall through to a synchronous commit in the same pass.
     */
    enum DeferredReason {
        WORKER_BUSY,
        GRID_LIMIT,
        HIGH_WATER,
        QUEUE_FULL
    }
}
