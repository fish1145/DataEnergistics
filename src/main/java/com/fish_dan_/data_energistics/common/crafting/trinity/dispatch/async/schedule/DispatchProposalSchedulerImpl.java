package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchLease;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposal;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.shard.ProviderShardDispatcher;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.CapacitySlicePlanner;

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
    private final ProviderShardDispatcher shardDispatcher;
    private final ThreadPoolExecutor executor;
    private final Set<WorkerKey> outstandingWorkers = new HashSet<>();
    private final Map<Long, Integer> outstandingByGrid = new HashMap<>();
    private final Map<Long, MutableMetrics> metricsByGrid = new HashMap<>();
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
        this.shardDispatcher = ProviderShardDispatcher.create(limits.shardCount());
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
        long gridGeneration = request.lease().gridGeneration();
        long queuedAtNanos = System.nanoTime();
        TicketImpl ticket;
        synchronized (this.admissionLock) {
            if (this.closed) {
                metrics(gridGeneration).recordRejected();
                return new Rejected(RejectionReason.CLOSED);
            }
            if (this.outstandingWorkers.contains(workerKey)) {
                metrics(gridGeneration).recordRejected();
                return new Rejected(RejectionReason.WORKER_BUSY);
            }
            int gridOutstanding = this.outstandingByGrid.getOrDefault(gridGeneration, 0);
            if (gridOutstanding >= this.limits.perGridOutstanding()) {
                metrics(gridGeneration).recordRejected();
                return new Rejected(RejectionReason.GRID_LIMIT);
            }
            ticket = new TicketImpl(this, request.lease(), workerKey, wakeup);
            this.outstandingWorkers.add(workerKey);
            this.outstandingByGrid.put(gridGeneration, Math.incrementExact(gridOutstanding));
            this.tickets.add(ticket);
        }

        FutureTask<Void> task = new FutureTask<>(() -> {
            calculate(ticket, request, queuedAtNanos);
            return null;
        });
        ticket.bind(task);
        try {
            this.executor.execute(task);
            synchronized (this.admissionLock) {
                metrics(gridGeneration).recordAdmitted();
            }
            return new Accepted(ticket);
        } catch (RejectedExecutionException exception) {
            ticket.close();
            synchronized (this.admissionLock) {
                metrics(gridGeneration).recordRejected();
            }
            return new Rejected(RejectionReason.QUEUE_FULL);
        }
    }

    @Override
    public DispatchProposalMetrics snapshotAndResetMetrics(long gridGeneration) {
        synchronized (this.admissionLock) {
            MutableMetrics metrics = this.metricsByGrid.remove(gridGeneration);
            int queueDepth = this.executor.getQueue().size();
            int outstanding = this.outstandingByGrid.getOrDefault(gridGeneration, 0);
            if (metrics == null) {
                return new DispatchProposalMetrics(
                        0,
                        0,
                        0,
                        0,
                        0,
                        0L,
                        0L,
                        queueDepth,
                        this.limits.queueCapacity(),
                        outstanding);
            }
            return metrics.snapshot(
                    queueDepth,
                    this.limits.queueCapacity(),
                    outstanding);
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

    private void calculate(
            TicketImpl ticket,
            CraftingDispatchProposalRequest request,
            long queuedAtNanos) {
        long startedAtNanos = System.nanoTime();
        long queueWaitNanos = elapsedNanos(queuedAtNanos, startedAtNanos);
        boolean failed = false;
        try {
            ProviderShardDispatcher.Result result = this.shardDispatcher.selectAndReserve(request, this.slicePlanner);
            switch (result) {
                case ProviderShardDispatcher.NoCapacity ignored ->
                        ticket.complete(DispatchProposalTicket.NoCapacity.INSTANCE);
                case ProviderShardDispatcher.Reserved reserved -> {
                    ticket.attachReservation(reserved.reservation());
                    ticket.complete(new DispatchProposalTicket.Ready(new CraftingDispatchProposal(
                            request.lease(),
                            reserved.target(),
                            reserved.logicalCrafts(),
                            reserved.nextCursor())));
                }
            }
        } catch (RuntimeException exception) {
            failed = true;
            Data_Energistics.LOGGER.error(
                    "Trinity dispatch proposal calculation failed for worker {} job {}",
                    request.lease().workerNumber(),
                    request.lease().jobId(),
                    exception);
            ticket.complete(new DispatchProposalTicket.Failed(exception));
        } finally {
            long calculationNanos = elapsedNanos(startedAtNanos, System.nanoTime());
            synchronized (this.admissionLock) {
                metrics(request.lease().gridGeneration()).recordCompleted(
                        queueWaitNanos,
                        calculationNanos,
                        failed);
            }
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

    private MutableMetrics metrics(long gridGeneration) {
        return this.metricsByGrid.computeIfAbsent(gridGeneration, ignored -> new MutableMetrics());
    }

    private static long elapsedNanos(long startedAtNanos, long completedAtNanos) {
        long elapsed = completedAtNanos - startedAtNanos;
        if (elapsed < 0L) {
            throw new IllegalStateException("Dispatch proposal nano clock moved backwards");
        }
        return elapsed;
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

    /**
     * Stable worker identity used only for outstanding admission.
     */
    private record WorkerKey(UUID runtimeId, int workerNumber) {

        private static WorkerKey from(CraftingDispatchLease lease) {
            return new WorkerKey(lease.runtimeId(), lease.workerNumber());
        }
    }

    /**
     * Guarded by {@link #admissionLock}; values are drained once per grid tick.
     */
    private static final class MutableMetrics {

        private int admitted;
        private int rejected;
        private int completed;
        private int failed;
        private int stale;
        private long queueWaitNanos;
        private long calculationNanos;

        private void recordAdmitted() {
            this.admitted = Math.incrementExact(this.admitted);
        }

        private void recordRejected() {
            this.rejected = Math.incrementExact(this.rejected);
        }

        private void recordCompleted(long queueWaitNanos, long calculationNanos, boolean failed) {
            this.completed = Math.incrementExact(this.completed);
            if (failed) {
                this.failed = Math.incrementExact(this.failed);
            }
            this.queueWaitNanos = Math.addExact(this.queueWaitNanos, queueWaitNanos);
            this.calculationNanos = Math.addExact(this.calculationNanos, calculationNanos);
        }

        private void recordStale() {
            this.stale = Math.incrementExact(this.stale);
        }

        private DispatchProposalMetrics snapshot(
                int queueDepth,
                int queueCapacity,
                int outstanding) {
            return new DispatchProposalMetrics(
                    this.admitted,
                    this.rejected,
                    this.completed,
                    this.failed,
                    this.stale,
                    this.queueWaitNanos,
                    this.calculationNanos,
                    queueDepth,
                    queueCapacity,
                    outstanding);
        }
    }

    /**
     * Thread-safe ticket whose terminal result remains available until the server thread closes it.
     */
    private static final class TicketImpl implements DispatchProposalTicket {

        private final DispatchProposalSchedulerImpl owner;
        private final CraftingDispatchLease lease;
        private final WorkerKey workerKey;
        private final Runnable wakeup;
        private final AtomicReference<State> state = new AtomicReference<>(Pending.INSTANCE);
        private final AtomicReference<ProviderShardDispatcher.Reservation> reservation = new AtomicReference<>();
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
        public void recordStale() {
            synchronized (this.owner.admissionLock) {
                this.owner.metrics(this.lease.gridGeneration()).recordStale();
            }
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
            ProviderShardDispatcher.Reservation currentReservation = this.reservation.getAndSet(null);
            if (currentReservation != null) {
                currentReservation.close();
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

        private void attachReservation(ProviderShardDispatcher.Reservation reservation) {
            if (!this.reservation.compareAndSet(null, reservation)) {
                reservation.close();
                throw new IllegalStateException("A dispatch proposal ticket already owns a provider reservation");
            }
            if (this.closed.get()) {
                ProviderShardDispatcher.Reservation cancelled = this.reservation.getAndSet(null);
                if (cancelled != null) {
                    cancelled.close();
                }
            }
        }
    }
}
