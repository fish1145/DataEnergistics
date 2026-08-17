package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.runtime;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchLease;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposal;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalPolicy;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalTicket;

import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Server-thread owner of bounded outstanding proposals for one Trinity worker.
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
    private final Map<Object, ProposalSlot> slots = new IdentityHashMap<>();
    @Nullable
    private Object lastPolledIdentity;

    TrinityWorkerProposalCoordinator(Supplier<DispatchProposalScheduler> scheduler) {
        if (scheduler == null) {
            throw new IllegalArgumentException("Dispatch proposal scheduler supplier must not be null");
        }
        this.scheduler = scheduler;
    }

    /**
     * Polls the proposal belonging to one work identity and cancels it when its lease changed.
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
        ProposalSlot slot = this.slots.get(workIdentity);
        this.lastPolledIdentity = workIdentity;
        if (slot == null) {
            return Empty.INSTANCE;
        }
        if (!slot.ticket().lease().equals(currentLease)) {
            discardStale(workIdentity);
            return Empty.INSTANCE;
        }
        return switch (slot.ticket().state()) {
            case DispatchProposalTicket.Pending ignored -> Pending.INSTANCE;
            case DispatchProposalTicket.Ready ready -> new Ready(ready.proposal());
            case DispatchProposalTicket.NoCapacity ignored -> terminal(workIdentity, NoCapacity.INSTANCE);
            case DispatchProposalTicket.Failed ignored -> terminal(
                    workIdentity,
                    new Fallback(FallbackReason.CALCULATION_FAILED));
            case DispatchProposalTicket.Cancelled ignored -> terminal(
                    workIdentity,
                    new Fallback(FallbackReason.CANCELLED));
        };
    }

    /**
     * Attempts to create one proposal for the supplied work identity.
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
        if (this.slots.containsKey(workIdentity)) {
            throw new IllegalStateException("A Trinity worker proposal is already outstanding");
        }
        DispatchProposalScheduler.Submission submission = this.scheduler.get().submit(request, wakeup, policy);
        return switch (submission) {
            case DispatchProposalScheduler.Accepted accepted -> {
                this.slots.put(workIdentity, new ProposalSlot(accepted.ticket()));
                yield Pending.INSTANCE;
            }
            case DispatchProposalScheduler.Rejected rejected -> rejectionDecision(rejected.reason());
        };
    }

    /**
     * @return whether any worker proposal is waiting for background completion
     */
    public boolean pending() {
        return this.slots.values().stream()
                .anyMatch(slot -> slot.ticket().state() instanceof DispatchProposalTicket.Pending);
    }

    /**
     * @return whether this worker still owns any unconsumed proposal ticket state
     */
    public boolean outstanding() {
        return !this.slots.isEmpty();
    }

    /**
     * Releases the currently consumed ready proposal after server-thread commit or rejection.
     */
    public void release() {
        releaseLast();
    }

    /**
     * Releases the proposal most recently selected by {@link #poll(CraftingDispatchLease, Object)}.
     */
    public void releaseLast() {
        if (this.lastPolledIdentity != null) {
            closeSlot(this.lastPolledIdentity);
            this.lastPolledIdentity = null;
        }
    }

    /**
     * Records and releases a proposal rejected by server-thread generation or route revalidation.
     */
    public void discardStale() {
        if (this.lastPolledIdentity != null) {
            discardStale(this.lastPolledIdentity);
        }
    }

    /**
     * Records and releases a proposal rejected by server-thread generation or route revalidation.
     *
     * @param workIdentity identity whose proposal became stale
     */
    public void discardStale(Object workIdentity) {
        if (workIdentity == null) {
            throw new IllegalArgumentException("Stale proposal work identity must not be null");
        }
        ProposalSlot slot = this.slots.get(workIdentity);
        if (slot != null) {
            slot.ticket().recordStale();
            closeSlot(workIdentity);
        }
        if (this.lastPolledIdentity == workIdentity) {
            this.lastPolledIdentity = null;
        }
    }

    /**
     * Cancels any outstanding proposal during job, route, reload or worker lifecycle changes.
     */
    public void cancel() {
        for (ProposalSlot slot : this.slots.values()) {
            slot.ticket().close();
        }
        this.slots.clear();
        this.lastPolledIdentity = null;
    }

    private Decision terminal(Object workIdentity, Decision decision) {
        closeSlot(workIdentity);
        return decision;
    }

    private void closeSlot(Object workIdentity) {
        ProposalSlot slot = this.slots.remove(workIdentity);
        if (slot != null) {
            slot.ticket().close();
        }
    }

    private record ProposalSlot(DispatchProposalTicket ticket) {}

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
