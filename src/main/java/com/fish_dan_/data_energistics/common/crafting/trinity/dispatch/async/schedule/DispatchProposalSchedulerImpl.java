package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchLease;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposal;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.shard.ProviderShardDispatcher;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.DispatchProposalCandidatePlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
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

    private final Object queueAdmissionLock = new Object();
    private final DispatchProposalLimits limits;
    private final DispatchProposalCandidatePlanner candidatePlanner;
    private final ProviderShardDispatcher shardDispatcher;
    private final ThreadPoolExecutor executor;
    private final ConcurrentMap<WorkerKey, TicketImpl> outstandingWorkers = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, GridAdmission> admissionsByGrid = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, MutableMetrics> metricsByGrid = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

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
        long queuedAtNanos = System.nanoTime();
        GridAdmission gridAdmission = this.admissionsByGrid.computeIfAbsent(
                gridGeneration,
                GridAdmission::new);
        Admission admission;
        synchronized (this.queueAdmissionLock) {
            admission = gridAdmission.admit(request.lease(), workerKey, wakeup, policy);
            if (admission instanceof AdmissionGranted granted) {
                TicketImpl ticket = granted.ticket();
                FutureTask<Void> task = new FutureTask<>(() -> {
                    calculate(ticket, request, policy.providerQuantum(), queuedAtNanos);
                    return null;
                });
                ticket.bind(task);
                try {
                    this.executor.execute(task);
                    if (this.closed.get()) {
                        ticket.close();
                        admission = new AdmissionRejected(RejectionReason.CLOSED, granted.metrics());
                    }
                } catch (RejectedExecutionException exception) {
                    ticket.close();
                    admission = new AdmissionRejected(RejectionReason.QUEUE_FULL, granted.metrics());
                }
            }
        }
        if (admission instanceof AdmissionRejected rejected) {
            rejected.metrics().recordRejected();
            return new Rejected(rejected.reason());
        }
        AdmissionGranted granted = (AdmissionGranted) admission;
        granted.metrics().recordAdmitted();
        return new Accepted(granted.ticket());
    }

    @Override
    public DispatchProposalMetrics snapshotAndResetMetrics(long gridGeneration) {
        return this.admissionsByGrid
                .computeIfAbsent(gridGeneration, GridAdmission::new)
                .snapshotAndResetMetrics();
    }

    @Override
    public void clearGrid(long gridGeneration) {
        if (gridGeneration <= 0L) {
            throw new IllegalArgumentException("Dispatch proposal Grid generation must be positive");
        }
        ClearedGrid cleared = this.admissionsByGrid
                .computeIfAbsent(gridGeneration, GridAdmission::new)
                .clear();
        if (cleared.metrics() != null) {
            cleared.metrics().retireAndReset();
        }
        cleared.tickets().forEach(TicketImpl::close);
    }

    @Override
    public void close() {
        if (!this.closed.compareAndSet(false, true)) {
            return;
        }
        Set<TicketImpl> closing = new HashSet<>();
        for (GridAdmission admission : this.admissionsByGrid.values()) {
            closing.addAll(admission.snapshotTickets());
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
        ticket.gridAdmission.release(ticket);
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
     * Per-Grid admission state. Only the queue check and executor handoff serialize globally; asynchronous completion,
     * metrics snapshots, and Grid cleanup stay on their own Grid state.
     */
    private final class GridAdmission {

        private final long gridGeneration;
        private final Set<TicketImpl> tickets = new HashSet<>();

        private GridAdmission(long gridGeneration) {
            this.gridGeneration = gridGeneration;
        }

        /**
         * Applies the original rejection order and registers one ticket while the caller owns the short global queue
         * admission boundary.
         */
        private synchronized Admission admit(
                                             CraftingDispatchLease lease,
                                             WorkerKey workerKey,
                                             Runnable wakeup,
                                             DispatchProposalPolicy policy) {
            MutableMetrics gridMetrics = metrics(this.gridGeneration);
            if (closed.get()) {
                return new AdmissionRejected(RejectionReason.CLOSED, gridMetrics);
            }
            if (!policy.enabled()) {
                return new AdmissionRejected(RejectionReason.DISABLED, gridMetrics);
            }
            if (outstandingWorkers.containsKey(workerKey)) {
                return new AdmissionRejected(RejectionReason.WORKER_BUSY, gridMetrics);
            }
            int gridLimit = Math.min(limits.perGridOutstanding(), policy.actorPermits());
            if (this.tickets.size() >= gridLimit) {
                return new AdmissionRejected(RejectionReason.GRID_LIMIT, gridMetrics);
            }
            if (policy.proposalHighWater() < limits.queueCapacity() &&
                    executor.getQueue().size() >= policy.proposalHighWater()) {
                return new AdmissionRejected(RejectionReason.HIGH_WATER, gridMetrics);
            }

            TicketImpl ticket = new TicketImpl(
                    DispatchProposalSchedulerImpl.this,
                    lease,
                    workerKey,
                    wakeup,
                    gridMetrics,
                    this);
            TicketImpl previous = outstandingWorkers.putIfAbsent(workerKey, ticket);
            if (previous != null) {
                return new AdmissionRejected(RejectionReason.WORKER_BUSY, gridMetrics);
            }
            if (!this.tickets.add(ticket)) {
                outstandingWorkers.remove(workerKey, ticket);
                throw new IllegalStateException("Dispatch proposal ticket was admitted twice");
            }
            return new AdmissionGranted(ticket, gridMetrics);
        }

        /**
         * Releases one ticket without touching admission state owned by another Grid.
         */
        private synchronized void release(TicketImpl ticket) {
            if (!this.tickets.remove(ticket)) {
                throw new IllegalStateException("Dispatch proposal ticket was not owned by its Grid admission");
            }
            if (!outstandingWorkers.remove(ticket.workerKey, ticket)) {
                throw new IllegalStateException("Dispatch proposal worker admission ownership was lost");
            }
        }

        /**
         * Captures the current generation boundary and retires only its existing metrics bucket. Tickets admitted
         * after this method returns belong to the next live boundary and are not cancelled.
         */
        private synchronized ClearedGrid clear() {
            return new ClearedGrid(
                    List.copyOf(this.tickets),
                    metricsByGrid.remove(this.gridGeneration));
        }

        private synchronized List<TicketImpl> snapshotTickets() {
            return List.copyOf(this.tickets);
        }

        private synchronized DispatchProposalMetrics snapshotAndResetMetrics() {
            int queueDepth = executor.getQueue().size();
            MutableMetrics gridMetrics = metricsByGrid.get(this.gridGeneration);
            if (gridMetrics == null) {
                return new DispatchProposalMetrics(
                        0,
                        0,
                        0,
                        0,
                        0,
                        0L,
                        0L,
                        queueDepth,
                        limits.queueCapacity(),
                        this.tickets.size());
            }
            return gridMetrics.snapshotAndReset(
                    queueDepth,
                    limits.queueCapacity(),
                    this.tickets.size());
        }
    }

    private sealed interface Admission permits AdmissionGranted, AdmissionRejected {

        MutableMetrics metrics();
    }

    private record AdmissionGranted(TicketImpl ticket, MutableMetrics metrics) implements Admission {}

    private record AdmissionRejected(RejectionReason reason, MutableMetrics metrics) implements Admission {}

    private record ClearedGrid(List<TicketImpl> tickets, @Nullable MutableMetrics metrics) {}

    /**
     * Per-Grid accumulator that keeps proposal completion paths off the submission queue lock.
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
        private final GridAdmission gridAdmission;
        private final AtomicReference<State> state = new AtomicReference<>(Pending.INSTANCE);
        private final AtomicReference<ProviderShardDispatcher.Reservation> reservation = new AtomicReference<>();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean wakeupFailed = new AtomicBoolean();
        private volatile FutureTask<Void> task;

        private TicketImpl(DispatchProposalSchedulerImpl owner,
                            CraftingDispatchLease lease,
                            WorkerKey workerKey,
                            Runnable wakeup,
                            MutableMetrics metrics,
                            GridAdmission gridAdmission) {
            this.owner = owner;
            this.lease = lease;
            this.workerKey = workerKey;
            this.wakeup = wakeup;
            this.metrics = metrics;
            this.gridAdmission = gridAdmission;
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
