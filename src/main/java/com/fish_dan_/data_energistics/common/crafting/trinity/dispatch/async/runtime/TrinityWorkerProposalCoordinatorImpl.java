package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.runtime;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchLease;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalTicket;

import java.util.function.Supplier;

/**
 * Identity-based worker coordinator that keeps all mutable context on the server thread.
 */
final class TrinityWorkerProposalCoordinatorImpl implements TrinityWorkerProposalCoordinator {

    private final Supplier<DispatchProposalScheduler> scheduler;
    private DispatchProposalTicket ticket;
    private Object workIdentity;

    TrinityWorkerProposalCoordinatorImpl(Supplier<DispatchProposalScheduler> scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("Dispatch proposal scheduler supplier must not be null");
        }
        this.scheduler = scheduler;
    }

    @Override
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
            cancel();
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

    @Override
    public Decision submit(CraftingDispatchProposalRequest request, Object workIdentity, Runnable wakeup) {
        if (request == null) {
            throw new IllegalArgumentException("Dispatch proposal request must not be null");
        }
        if (workIdentity == null) {
            throw new IllegalArgumentException("Dispatch work identity must not be null");
        }
        if (wakeup == null) {
            throw new IllegalArgumentException("Dispatch proposal wakeup must not be null");
        }
        if (this.ticket != null) {
            throw new IllegalStateException("A Trinity worker proposal is already outstanding");
        }
        DispatchProposalScheduler.Submission submission = this.scheduler.get().submit(request, wakeup);
        return switch (submission) {
            case DispatchProposalScheduler.Accepted accepted -> {
                this.ticket = accepted.ticket();
                this.workIdentity = workIdentity;
                yield Pending.INSTANCE;
            }
            case DispatchProposalScheduler.Rejected rejected -> rejectionDecision(rejected.reason());
        };
    }

    @Override
    public boolean pending() {
        return this.ticket != null && this.ticket.state() instanceof DispatchProposalTicket.Pending;
    }

    @Override
    public boolean outstanding() {
        return this.ticket != null;
    }

    @Override
    public void release() {
        cancel();
    }

    @Override
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
            case QUEUE_FULL -> new Deferred(DeferredReason.QUEUE_FULL);
            case CLOSED -> new Fallback(FallbackReason.SCHEDULER_CLOSED);
        };
    }
}
