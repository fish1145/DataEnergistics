package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.runtime;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchLease;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposal;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalPolicy;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalTicket;

import java.util.function.Supplier;

/**
 * Server-thread owner of the single outstanding proposal permitted for one Trinity worker.
 *
 * <p>
 * The coordinator retains an opaque work object only for server-thread identity comparison. The scheduler receives
 * exclusively the immutable request and never observes this object.
 * </p>
 * <p>
 * Identity-based worker coordinator that keeps all mutable context on the server thread.
 */
public final class TrinityWorkerProposalCoordinator {

    /**
     * Creates an idle coordinator.
     *
     * @param scheduler running scheduler supplier resolved only when new work is submitted
     * @return independent worker coordinator
     */
    public static TrinityWorkerProposalCoordinator create(Supplier<DispatchProposalScheduler> scheduler) {
        return new TrinityWorkerProposalCoordinator(scheduler);
    }

    /**
     * Retains the pre-Governor contract with deterministic hard limits.
     */
    public Decision submit(CraftingDispatchProposalRequest request, Object workIdentity, Runnable wakeup) {
        return submit(request, workIdentity, wakeup, DispatchProposalPolicy.defaults());
    }

    /**
     * Non-blocking server-thread decision.
     */
    public sealed interface Decision permits Empty, Pending, Ready, NoCapacity, Deferred, Fallback {}

    /**
     * No proposal exists, so the caller may capture and submit immutable candidates.
     */
    public enum Empty implements Decision {
        INSTANCE
    }

    /**
     * The outstanding proposal is queued or calculating.
     */
    public enum Pending implements Decision {
        INSTANCE
    }

    /**
     * @param proposal completed immutable proposal awaiting server-thread revalidation
     */
    public record Ready(CraftingDispatchProposal proposal) implements Decision {

        public Ready {
            if (proposal == null) {
                throw new IllegalArgumentException("Ready worker dispatch proposal must not be null");
            }
        }
    }

    /**
     * No immutable candidate had a safe positive offer.
     */
    public enum NoCapacity implements Decision {
        INSTANCE
    }

    /**
     * @param reason bounded scheduler pressure requiring a retry without synchronous resource mutation
     */
    public record Deferred(DeferredReason reason) implements Decision {

        public Deferred {
            if (reason == null) {
                throw new IllegalArgumentException("Worker proposal deferral reason must not be null");
            }
        }
    }

    /**
     * @param reason expected reason to use the Phase 3 synchronous path
     */
    public record Fallback(FallbackReason reason) implements Decision {

        public Fallback {
            if (reason == null) {
                throw new IllegalArgumentException("Worker proposal fallback reason must not be null");
            }
        }
    }

    /**
     * Expected proposal failures that never authorize resource mutation in the background.
     */
    public enum FallbackReason {
        SCHEDULER_CLOSED,
        SCHEDULER_DISABLED,
        CALCULATION_FAILED,
        CANCELLED
    }

    /**
     * Expected bounded-admission pressure that must not fall through to a synchronous commit in the same pass.
     */
    public enum DeferredReason {
        WORKER_BUSY,
        GRID_LIMIT,
        HIGH_WATER,
        GLOBAL_LIMIT
    }

    private final Supplier<DispatchProposalScheduler> scheduler;
    private DispatchProposalTicket ticket;
    private Object workIdentity;

    TrinityWorkerProposalCoordinator(Supplier<DispatchProposalScheduler> scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("Dispatch proposal scheduler supplier must not be null");
        }
        this.scheduler = scheduler;
    }

    /**
     * Polls the existing proposal and cancels it when any lease or server-thread work identity changed.
     *
     * @param currentLease current fully recaptured generation lease
     * @param workIdentity exact current legacy task or compact work object
     * @return non-blocking proposal decision
     */
    public Decision poll(CraftingDispatchLease currentLease, Object workIdentity) {
        if (currentLease == null) {
            throw new IllegalArgumentException("Current dispatch proposal lease must not be null");
        }
        if (workIdentity == null) {
            throw new IllegalArgumentException("Current dispatch work identity must not be null");
        }
        if (this.ticket == null) {
            return Empty.INSTANCE;
        }
        if (!this.ticket.lease().equals(currentLease) || this.workIdentity != workIdentity) {
            discardStale();
            return Empty.INSTANCE;
        }
        return switch (this.ticket.state()) {
            case DispatchProposalTicket.Pending ignored -> Pending.INSTANCE;
            case DispatchProposalTicket.Ready ready -> new Ready(ready.proposal());
            case DispatchProposalTicket.NoCapacity ignored -> terminal(NoCapacity.INSTANCE);
            case DispatchProposalTicket.Failed ignored -> terminal(new Fallback(FallbackReason.CALCULATION_FAILED));
            case DispatchProposalTicket.Cancelled ignored -> terminal(new Fallback(FallbackReason.CANCELLED));
        };
    }

    /**
     * Attempts to create the worker's only outstanding proposal.
     *
     * @param request      immutable pure-planning request
     * @param workIdentity exact server-thread work identity retained for later stale validation
     * @param wakeup       callback that enqueues only this worker after completion
     * @param policy       current per-grid Governor proposal policy
     * @return pending or synchronous-fallback decision
     */
    public Decision submit(
                           CraftingDispatchProposalRequest request,
                           Object workIdentity,
                           Runnable wakeup,
                           DispatchProposalPolicy policy) {
        if (request == null) {
            throw new IllegalArgumentException("Dispatch proposal request must not be null");
        }
        if (workIdentity == null) {
            throw new IllegalArgumentException("Dispatch work identity must not be null");
        }
        if (wakeup == null) {
            throw new IllegalArgumentException("Dispatch proposal wakeup must not be null");
        }
        if (policy == null) {
            throw new IllegalArgumentException("Dispatch proposal policy must not be null");
        }
        if (this.ticket != null) {
            throw new IllegalStateException("A Trinity worker proposal is already outstanding");
        }
        DispatchProposalScheduler.Submission submission = this.scheduler.get().submit(request, wakeup, policy);
        return switch (submission) {
            case DispatchProposalScheduler.Accepted accepted -> {
                this.ticket = accepted.ticket();
                this.workIdentity = workIdentity;
                yield Pending.INSTANCE;
            }
            case DispatchProposalScheduler.Rejected rejected -> rejectionDecision(rejected.reason());
        };
    }

    /**
     * @return whether this worker is waiting exclusively for its background proposal completion
     */
    public boolean pending() {
        return this.ticket != null && this.ticket.state() instanceof DispatchProposalTicket.Pending;
    }

    /**
     * @return whether this worker still owns any unconsumed proposal ticket state
     */
    public boolean outstanding() {
        return this.ticket != null;
    }

    /**
     * Releases the currently consumed ready proposal after server-thread commit or rejection.
     */
    public void release() {
        cancel();
    }

    /**
     * Records and releases a proposal rejected by server-thread generation or route revalidation.
     */
    public void discardStale() {
        if (this.ticket != null) {
            this.ticket.recordStale();
        }
        cancel();
    }

    /**
     * Cancels any outstanding proposal during job, route, reload or worker lifecycle changes.
     */
    public void cancel() {
        DispatchProposalTicket closing = this.ticket;
        this.ticket = null;
        this.workIdentity = null;
        if (closing != null) {
            closing.close();
        }
    }

    private Decision terminal(Decision decision) {
        release();
        return decision;
    }

    private static Decision rejectionDecision(DispatchProposalScheduler.RejectionReason reason) {
        return switch (reason) {
            case WORKER_BUSY -> new Deferred(DeferredReason.WORKER_BUSY);
            case GRID_LIMIT -> new Deferred(DeferredReason.GRID_LIMIT);
            case HIGH_WATER -> new Deferred(DeferredReason.HIGH_WATER);
            case GLOBAL_LIMIT -> new Deferred(DeferredReason.GLOBAL_LIMIT);
            case DISABLED -> new Fallback(FallbackReason.SCHEDULER_DISABLED);
            case CLOSED -> new Fallback(FallbackReason.SCHEDULER_CLOSED);
        };
    }
}
