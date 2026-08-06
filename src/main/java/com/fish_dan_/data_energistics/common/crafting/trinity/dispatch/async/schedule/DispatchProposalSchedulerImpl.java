package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchLease;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposal;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.shard.ProviderShardDispatcher;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.DispatchProposalCandidatePlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Fixed bounded implementation enforcing one proposal per worker and a separate per-grid outstanding limit.
 */
final class DispatchProposalSchedulerImpl implements DispatchProposalScheduler {

    private static final AtomicInteger THREAD_SEQUENCE = new AtomicInteger();

    private final Object admissionLock = new Object();
    private final DispatchProposalLimits limits;
    private final DispatchProposalCandidatePlanner candidatePlanner;
    private final ProviderShardDispatcher shardDispatcher;
    private final ThreadPoolExecutor executor;
    private final Set<WorkerKey> outstandingWorkers = new HashSet<>();
    private final Map<Long, Integer> outstandingByGrid = new HashMap<>();
    private final ConcurrentMap<Long, MutableMetrics> metricsByGrid = new ConcurrentHashMap<>();
    private final Set<TicketImpl> tickets = new HashSet<>();
    private boolean closed;

    DispatchProposalSchedulerImpl(DispatchProposalLimits limits, Supplier<TrinityComputationCache> computationCache) {
        this(limits, DispatchProposalCandidatePlanner.create(computationCache));
    }

    DispatchProposalSchedulerImpl(DispatchProposalLimits limits,
                                  DispatchProposalCandidatePlanner candidatePlanner) {
        if (limits == null) {
            throw new IllegalArgumentException("Dispatch proposal limits must not be null");
        }
        if (candidatePlanner == null) {
            throw new IllegalArgumentException("Dispatch proposal candidate planner must not be null");
        }
        this.limits = limits;
        this.candidatePlanner = candidatePlanner;
        this.shardDispatcher = ProviderShardDispatcher.create(limits.shardCount());
        this.executor = createExecutor(limits);
    }

    @Override
    public Submission submit(
                             CraftingDispatchProposalRequest request,
                             Runnable wakeup,
                             DispatchProposalPolicy policy) {
        if (request == null) {
            throw new IllegalArgumentException("Dispatch proposal request must not be null");
        }
        if (wakeup == null) {
            throw new IllegalArgumentException("Dispatch proposal wakeup must not be null");
        }
        if (policy == null) {
            throw new IllegalArgumentException("Dispatch proposal policy must not be null");
        }

        WorkerKey workerKey = WorkerKey.from(request.lease());
        long gridGeneration = request.lease().gridGeneration();
        MutableMetrics gridMetrics;
        long queuedAtNanos = System.nanoTime();
        TicketImpl ticket;
        RejectionReason rejectionReason;
        synchronized (this.admissionLock) {
            gridMetrics = metrics(gridGeneration);
            int gridOutstanding = this.outstandingByGrid.getOrDefault(gridGeneration, 0);
            rejectionReason = admissionRejection(workerKey, gridOutstanding, policy);
            if (rejectionReason == null) {
                ticket = new TicketImpl(this, request.lease(), workerKey, wakeup, gridMetrics);
                this.outstandingWorkers.add(workerKey);
                this.outstandingByGrid.put(gridGeneration, Math.incrementExact(gridOutstanding));
                this.tickets.add(ticket);
            } else {
                ticket = null;
            }
        }
        if (rejectionReason != null) {
            gridMetrics.recordRejected();
            return new Rejected(rejectionReason);
        }

        FutureTask<Void> task = new FutureTask<>(() -> {
            calculate(ticket, request, policy.providerQuantum(), queuedAtNanos);
            return null;
        });
        ticket.bind(task);
        try {
            this.executor.execute(task);
            gridMetrics.recordAdmitted();
            return new Accepted(ticket);
        } catch (RejectedExecutionException exception) {
            ticket.close();
            gridMetrics.recordRejected();
            return new Rejected(RejectionReason.QUEUE_FULL);
        }
    }

    @Override
    public DispatchProposalMetrics snapshotAndResetMetrics(long gridGeneration) {
        int outstanding;
        synchronized (this.admissionLock) {
            outstanding = this.outstandingByGrid.getOrDefault(gridGeneration, 0);
        }
        int queueDepth = this.executor.getQueue().size();
        MutableMetrics metrics = this.metricsByGrid.get(gridGeneration);
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
        return metrics.snapshotAndReset(
                queueDepth,
                this.limits.queueCapacity(),
                outstanding);
    }

    @Override
    public void clearGrid(long gridGeneration) {
        if (gridGeneration <= 0L) {
            throw new IllegalArgumentException("Dispatch proposal Grid generation must be positive");
        }
        List<TicketImpl> gridTickets;
        MutableMetrics retiredMetrics;
        synchronized (this.admissionLock) {
            gridTickets = this.tickets.stream()
                    .filter(ticket -> ticket.lease.gridGeneration() == gridGeneration)
                    .toList();
            retiredMetrics = this.metricsByGrid.remove(gridGeneration);
        }
        if (retiredMetrics != null) {
            retiredMetrics.retireAndReset();
        }
        gridTickets.forEach(TicketImpl::close);
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
                           int providerQuantum,
                           long queuedAtNanos) {
        long startedAtNanos = System.nanoTime();
        long queueWaitNanos = elapsedNanos(queuedAtNanos, startedAtNanos);
        boolean failed = false;
        try {
            ProviderShardDispatcher.Result result = this.shardDispatcher.selectAndReserve(
                    request,
                    this.candidatePlanner,
                    providerQuantum,
                    () -> !ticket.closed());
            switch (result) {
                case ProviderShardDispatcher.NoCapacity ignored -> ticket.complete(DispatchProposalTicket.NoCapacity.INSTANCE);
                case ProviderShardDispatcher.Reserved reserved -> {
                    ticket.attachReservation(reserved.reservation());
                    ticket.complete(new DispatchProposalTicket.Ready(new CraftingDispatchProposal(
                            request.lease(),
                            reserved.target(),
                            reserved.logicalCrafts(),
                            reserved.nextCursor())));
                }
            }
        } catch (CancellationException exception) {
            if (!ticket.closed()) {
                failed = true;
                Data_Energistics.LOGGER.error(
                        "Trinity dispatch proposal calculation was unexpectedly cancelled for worker {} job {}",
                        request.lease().workerNumber(),
                        request.lease().jobId(),
                        exception);
                ticket.complete(new DispatchProposalTicket.Failed(exception));
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
            failed |= ticket.wakeupFailed();
            long calculationNanos = elapsedNanos(startedAtNanos, System.nanoTime());
            if (!ticket.closed()) {
                ticket.metrics.recordCompleted(
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

    /**
     * Evaluates the lock-protected admission state without recording metrics; {@code null} means admitted.
     */
    private RejectionReason admissionRejection(
                                               WorkerKey workerKey,
                                               int gridOutstanding,
                                               DispatchProposalPolicy policy) {
        if (this.closed) {
            return RejectionReason.CLOSED;
        }
        if (!policy.enabled()) {
            return RejectionReason.DISABLED;
        }
        if (this.outstandingWorkers.contains(workerKey)) {
            return RejectionReason.WORKER_BUSY;
        }
        if (gridOutstanding >= Math.min(this.limits.perGridOutstanding(), policy.actorPermits())) {
            return RejectionReason.GRID_LIMIT;
        }
        if (policy.proposalHighWater() < this.limits.queueCapacity() &&
                this.executor.getQueue().size() >= policy.proposalHighWater()) {
            return RejectionReason.HIGH_WATER;
        }
        return null;
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
     * Per-Grid accumulator that keeps unrelated Grid completion paths off the global admission lock.
     */
    private static final class MutableMetrics {

        private int admitted;
        private int rejected;
        private int completed;
        private int failed;
        private int stale;
        private long queueWaitNanos;
        private long calculationNanos;
        /** Prevents late work owned by a cleared Grid from republishing metrics through a captured bucket. */
        private boolean retired;

        private synchronized void recordAdmitted() {
            if (this.retired) {
                return;
            }
            this.admitted = Math.incrementExact(this.admitted);
        }

        private synchronized void recordRejected() {
            if (this.retired) {
                return;
            }
            this.rejected = Math.incrementExact(this.rejected);
        }

        private synchronized void recordCompleted(long queueWaitNanos, long calculationNanos, boolean failed) {
            if (this.retired) {
                return;
            }
            int nextCompleted = Math.incrementExact(this.completed);
            int nextFailed = failed ? Math.incrementExact(this.failed) : this.failed;
            long nextQueueWaitNanos = Math.addExact(this.queueWaitNanos, queueWaitNanos);
            long nextCalculationNanos = Math.addExact(this.calculationNanos, calculationNanos);
            this.completed = nextCompleted;
            this.failed = nextFailed;
            this.queueWaitNanos = nextQueueWaitNanos;
            this.calculationNanos = nextCalculationNanos;
        }

        private synchronized void recordStale() {
            if (this.retired) {
                return;
            }
            this.stale = Math.incrementExact(this.stale);
        }

        private synchronized void retireAndReset() {
            this.retired = true;
            reset();
        }

        private synchronized DispatchProposalMetrics snapshotAndReset(
                                                                      int queueDepth,
                                                                      int queueCapacity,
                                                                      int outstanding) {
            DispatchProposalMetrics snapshot = new DispatchProposalMetrics(
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
            reset();
            return snapshot;
        }

        private void reset() {
            this.admitted = 0;
            this.rejected = 0;
            this.completed = 0;
            this.failed = 0;
            this.stale = 0;
            this.queueWaitNanos = 0L;
            this.calculationNanos = 0L;
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
        private final MutableMetrics metrics;
        private final AtomicReference<State> state = new AtomicReference<>(Pending.INSTANCE);
        private final AtomicReference<ProviderShardDispatcher.Reservation> reservation = new AtomicReference<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean wakeupFailed = new AtomicBoolean();
        private volatile FutureTask<Void> task;

        private TicketImpl(DispatchProposalSchedulerImpl owner,
                            CraftingDispatchLease lease,
                            WorkerKey workerKey,
                            Runnable wakeup,
                            MutableMetrics metrics) {
            this.owner = owner;
            this.lease = lease;
            this.workerKey = workerKey;
            this.wakeup = wakeup;
            this.metrics = metrics;
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
            this.metrics.recordStale();
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
                this.wakeupFailed.set(true);
                Data_Energistics.LOGGER.error(
                        "Trinity dispatch proposal wakeup failed for worker {} job {}",
                        this.lease.workerNumber(),
                        this.lease.jobId(),
                        exception);
                close();
            }
        }

        private boolean wakeupFailed() {
            return this.wakeupFailed.get();
        }

        private boolean closed() {
            return this.closed.get();
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
