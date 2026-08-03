package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchLease;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposal;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.CapacitySlice;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.CapacitySlicePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.CapacitySlicePlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fixed bounded implementation enforcing one proposal per worker and a separate per-grid outstanding limit.
 */
final class DispatchProposalSchedulerImpl implements DispatchProposalScheduler {

    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    private final Object admissionLock = new Object();
    private final DispatchProposalLimits limits;
    private final CapacitySlicePlanner slicePlanner;
    private final ThreadPoolExecutor executor;
    private final Set<WorkerKey> outstandingWorkers = new HashSet<>();
    private final Map<Long, Integer> outstandingByGrid = new HashMap<>();
    private final Set<TicketImpl> tickets = new HashSet<>();
    private boolean closed;

    DispatchProposalSchedulerImpl(DispatchProposalLimits limits) {
        this(limits, CapacitySlicePlanner.create());
    }

    DispatchProposalSchedulerImpl(DispatchProposalLimits limits, CapacitySlicePlanner slicePlanner) {
        if (limits == null) {
            throw new IllegalArgumentException("Dispatch proposal limits must not be null");
        }
        if (slicePlanner == null) {
            throw new IllegalArgumentException("Dispatch proposal slice planner must not be null");
        }
        this.limits = limits;
        this.slicePlanner = slicePlanner;
        this.executor = createExecutor(limits);
    }

    @Override
    public Submission submit(CraftingDispatchProposalRequest request, Runnable wakeup) {
        if (request == null) {
            throw new IllegalArgumentException("Dispatch proposal request must not be null");
        }
        if (wakeup == null) {
            throw new IllegalArgumentException("Dispatch proposal wakeup must not be null");
        }

        WorkerKey workerKey = WorkerKey.from(request.lease());
        TicketImpl ticket;
        synchronized (this.admissionLock) {
            if (this.closed) {
                return new Rejected(RejectionReason.CLOSED);
            }
            if (this.outstandingWorkers.contains(workerKey)) {
                return new Rejected(RejectionReason.WORKER_BUSY);
            }
            int gridOutstanding = this.outstandingByGrid.getOrDefault(request.lease().gridGeneration(), 0);
            if (gridOutstanding >= this.limits.perGridOutstanding()) {
                return new Rejected(RejectionReason.GRID_LIMIT);
            }
            ticket = new TicketImpl(this, request.lease(), workerKey, wakeup);
            this.outstandingWorkers.add(workerKey);
            this.outstandingByGrid.put(request.lease().gridGeneration(), Math.incrementExact(gridOutstanding));
            this.tickets.add(ticket);
        }

        FutureTask<Void> task = new FutureTask<>(() -> {
            calculate(ticket, request);
            return null;
        });
        ticket.bind(task);
        try {
            this.executor.execute(task);
            return new Accepted(ticket);
        } catch (RejectedExecutionException exception) {
            ticket.close();
            return new Rejected(RejectionReason.QUEUE_FULL);
        }
    }

    @Override
    public void close() {
        Set<TicketImpl> closing;
        synchronized (this.admissionLock) {
            if (this.closed) {
                return;
            }
            this.closed = true;
            closing = Set.copyOf(this.tickets);
        }
        closing.forEach(TicketImpl::close);
        this.executor.shutdownNow();
    }

    private void calculate(TicketImpl ticket, CraftingDispatchProposalRequest request) {
        try {
            CapacitySlicePlan plan = this.slicePlanner.plan(
                    request.candidates(),
                    request.remainingCrafts(),
                    1,
                    request.cursor());
            if (plan.slices().isEmpty()) {
                ticket.complete(DispatchProposalTicket.NoCapacity.INSTANCE);
                return;
            }
            CapacitySlice slice = plan.slices().getFirst();
            ProviderCapacitySnapshot target = slice.target();
            long logicalCrafts = offeredCount(target, slice, request.remainingCrafts().longValueExact());
            ticket.complete(new DispatchProposalTicket.Ready(new CraftingDispatchProposal(
                    request.lease(),
                    target,
                    logicalCrafts,
                    plan.nextCursor())));
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Trinity dispatch proposal calculation failed for worker {} job {}",
                    request.lease().workerNumber(),
                    request.lease().jobId(),
                    exception);
            ticket.complete(new DispatchProposalTicket.Failed(exception));
        }
    }

    private void release(TicketImpl ticket) {
        synchronized (this.admissionLock) {
            if (!this.tickets.remove(ticket)) {
                return;
            }
            this.outstandingWorkers.remove(ticket.workerKey);
            long gridGeneration = ticket.lease.gridGeneration();
            int remaining = Math.decrementExact(this.outstandingByGrid.get(gridGeneration));
            if (remaining == 0) {
                this.outstandingByGrid.remove(gridGeneration);
            } else {
                this.outstandingByGrid.put(gridGeneration, remaining);
            }
        }
    }

    private void cancelTask(FutureTask<Void> task) {
        task.cancel(true);
        this.executor.remove(task);
    }

    private static ThreadPoolExecutor createExecutor(DispatchProposalLimits limits) {
        ThreadFactory threadFactory = task -> {
            Thread thread = new Thread(
                    task,
                    "DataEnergistics-TrinityDispatch-" + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(
                limits.workerThreads(),
                limits.workerThreads(),
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(limits.queueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    private static long offeredCount(
                                     ProviderCapacitySnapshot snapshot,
                                     CapacitySlice slice,
                                     long maximumCount) {
        return switch (snapshot.routingMode()) {
            case TARGETED -> Math.min(slice.logicalCrafts(), maximumCount);
            case AGGREGATE -> maximumCount;
            case ORDERED, UNKNOWN -> 1L;
        };
    }

    /** Stable worker identity used only for outstanding admission. */
    private record WorkerKey(UUID runtimeId, int workerNumber) {

        private static WorkerKey from(CraftingDispatchLease lease) {
            return new WorkerKey(lease.runtimeId(), lease.workerNumber());
        }
    }

    /** Thread-safe ticket whose terminal result remains available until the server thread closes it. */
    private static final class TicketImpl implements DispatchProposalTicket {

        private final DispatchProposalSchedulerImpl owner;
        private final CraftingDispatchLease lease;
        private final WorkerKey workerKey;
        private final Runnable wakeup;
        private final AtomicReference<State> state = new AtomicReference<>(Pending.INSTANCE);
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile FutureTask<Void> task;

        private TicketImpl(DispatchProposalSchedulerImpl owner,
                           CraftingDispatchLease lease,
                           WorkerKey workerKey,
                           Runnable wakeup) {
            this.owner = owner;
            this.lease = lease;
            this.workerKey = workerKey;
            this.wakeup = wakeup;
        }

        @Override
        public CraftingDispatchLease lease() {
            return this.lease;
        }

        @Override
        public State state() {
            return this.state.get();
        }

        @Override
        public void close() {
            if (!this.closed.compareAndSet(false, true)) {
                return;
            }
            this.state.set(Cancelled.INSTANCE);
            FutureTask<Void> currentTask = this.task;
            if (currentTask != null) {
                this.owner.cancelTask(currentTask);
            }
            this.owner.release(this);
        }

        private void bind(FutureTask<Void> task) {
            this.task = task;
            if (this.closed.get()) {
                this.owner.cancelTask(task);
            }
        }

        private void complete(State completedState) {
            if (!this.state.compareAndSet(Pending.INSTANCE, completedState) || this.closed.get()) {
                return;
            }
            try {
                this.wakeup.run();
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Trinity dispatch proposal wakeup failed for worker {} job {}",
                        this.lease.workerNumber(),
                        this.lease.jobId(),
                        exception);
                close();
            }
        }
    }
}
