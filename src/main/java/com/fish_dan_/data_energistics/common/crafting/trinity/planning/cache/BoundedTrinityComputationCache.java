package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Access-order LRU implementation with pinned in-flight entries and lifecycle-aware bypass tasks.
 */
final class BoundedTrinityComputationCache implements TrinityComputationCache {

    private final Object cacheLock = new Object();
    private final Executor executor;
    private final int gridEntryLimit;
    private final Long2ObjectOpenHashMap<GridPartition> partitions = new Long2ObjectOpenHashMap<>();
    private final ThreadLocal<@Nullable CacheEntry<?>> inlineOwnerEntry = new ThreadLocal<>();
    private boolean closed;

    BoundedTrinityComputationCache(Executor executor, int gridEntryLimit) {
        if (gridEntryLimit <= 0) {
            throw new IllegalArgumentException("A Trinity computation cache requires a positive Grid entry limit");
        }
        this.executor = executor;
        this.gridEntryLimit = gridEntryLimit;
    }

    @Override
    public <K, V> Optional<TrinityComputationValue<V>> computeInlineIfActive(
                                                                             long gridScope,
                                                                             TrinityComputationNamespace namespace,
                                                                             long revision,
                                                                             K key,
                                                                             BooleanSupplier lifecycleActive,
                                                                             Callable<TrinityCachedComputation<V>> calculation)
                                                                                                                                throws InterruptedException, ExecutionException {
        validateKey(gridScope, namespace, revision);

        CacheEntry<V> entry;
        CacheEntry<?> parentEntry;
        SubscriberLease<V> lease;
        boolean cacheHit;
        boolean registered;
        boolean staleRevision;
        ObjectList<CacheEntry<?>> cancelled = new ObjectArrayList<>();
        synchronized (this.cacheLock) {
            requireOpen();
            if (!lifecycleActive.getAsBoolean()) {
                return Optional.empty();
            }
            parentEntry = this.inlineOwnerEntry.get();
            if (parentEntry != null) {
                parentEntry.requireInlineAdmission();
            }
            GridPartition partition = this.partitions.computeIfAbsent(gridScope, GridPartition::new);
            ScopedKey scopedKey = new ScopedKey(namespace, revision, key);
            staleRevision = advanceRevision(partition, namespace, revision, cancelled);
            CacheEntry<?> existing = partition.registeredEntry(scopedKey);
            boolean existingRegistered = true;
            if (existing == null) {
                existing = partition.bypassEntry(scopedKey);
                existingRegistered = false;
            }
            if (existing != null) {
                entry = castEntry(existing);
                cacheHit = true;
                registered = existingRegistered;
            } else if (staleRevision) {
                entry = null;
                cacheHit = false;
                registered = false;
            } else {
                cacheHit = false;
                registered = reserveRegisteredSlot(partition);
                entry = new CacheEntry<>(partition, scopedKey, calculation, registered, true);
                if (registered) {
                    partition.entries.put(scopedKey, entry);
                } else {
                    partition.bypassEntries.put(scopedKey, entry);
                }
            }
            if (entry == null) {
                lease = null;
            } else {
                lease = new SubscriberLease<>(entry);
                if (parentEntry != null) {
                    parentEntry.attachInline(lease, !cacheHit);
                }
            }
        }
        cancelled.forEach(CacheEntry::cancelObsolete);

        if (entry == null) {
            throw new CancellationException("The Trinity computation revision is obsolete");
        }

        V value;
        try {
            if (!cacheHit) {
                entry.execution.run();
            }
            value = entry.result.get();
        } catch (InterruptedException exception) {
            lease.release(true);
            throw exception;
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw exception;
        } finally {
            if (parentEntry != null) {
                synchronized (this.cacheLock) {
                    parentEntry.clearInline(lease);
                }
            }
            lease.release(false);
        }
        if (parentEntry != null && parentEntry.state.get() != CacheEntryState.ACTIVE) {
            throw new CancellationException("The owning Trinity planning request was cancelled");
        }
        return Optional.of(new TrinityComputationValue<>(value, cacheHit, registered));
    }

    @Override
    public <K, V> Optional<V> getIfPresent(
                                           long gridScope,
                                           TrinityComputationNamespace namespace,
                                           long revision,
                                           K key) {
        validateKey(gridScope, namespace, revision);
        synchronized (this.cacheLock) {
            requireOpen();
            if (!this.partitions.containsKey(gridScope)) {
                return Optional.empty();
            }
            GridPartition partition = this.partitions.get(gridScope);
            CacheEntry<?> existing = partition.registeredEntry(new ScopedKey(namespace, revision, key));
            if (existing == null || existing.state.get() != CacheEntryState.PUBLISHED ||
                    !existing.result.isDone() ||
                    existing.result.isCancelled() || existing.result.isCompletedExceptionally()) {
                return Optional.empty();
            }
            CacheEntry<V> entry = castEntry(existing);
            return Optional.of(entry.result.join());
        }
    }

    @Override
    public <K, V> void publishIfAbsent(
                                       long gridScope,
                                       TrinityComputationNamespace namespace,
                                       long revision,
                                       K key,
                                       V value) {
        validateKey(gridScope, namespace, revision);
        ObjectList<CacheEntry<?>> cancelled = new ObjectArrayList<>();
        synchronized (this.cacheLock) {
            requireOpen();
            GridPartition partition = this.partitions.computeIfAbsent(gridScope, GridPartition::new);
            ScopedKey scopedKey = new ScopedKey(namespace, revision, key);
            boolean staleRevision = advanceRevision(partition, namespace, revision, cancelled);
            if (!staleRevision && !partition.entries.containsKey(scopedKey) &&
                    !partition.bypassEntries.containsKey(scopedKey) && reserveRegisteredSlot(partition)) {
                CacheEntry<V> entry = new CacheEntry<>(
                        partition,
                        scopedKey,
                        () -> TrinityCachedComputation.cacheable(value),
                        true,
                        true);
                entry.state.set(CacheEntryState.PUBLISHED);
                entry.result.complete(value);
                partition.entries.put(scopedKey, entry);
            }
        }
        cancelled.forEach(CacheEntry::cancelObsolete);
    }

    @Override
    public <V> Future<V> submit(long gridScope, Callable<V> calculation) {
        return submit(this.executor, gridScope, calculation);
    }

    @Override
    public <V> Future<V> submit(Executor executionLane, long gridScope, Callable<V> calculation) {
        if (gridScope < 0L) {
            throw new IllegalArgumentException("A detached Trinity computation requires a non-negative Grid scope");
        }
        CacheEntry<V> entry;
        CallerFuture<V> callerFuture;
        synchronized (this.cacheLock) {
            requireOpen();
            GridPartition partition = this.partitions.computeIfAbsent(gridScope, GridPartition::new);
            ScopedKey scopedKey = new ScopedKey(
                    TrinityComputationNamespace.PLANNING_REQUEST,
                    SEMANTIC_REVISION,
                    new Object());
            entry = new CacheEntry<>(
                    partition,
                    scopedKey,
                    () -> TrinityCachedComputation.transientValue(calculation.call()),
                    false,
                    false);
            partition.bypassEntries.put(scopedKey, entry);
            callerFuture = new CallerFuture<>(entry);
        }
        try {
            executionLane.execute(entry.execution);
        } catch (RejectedExecutionException exception) {
            entry.reject(exception);
            throw exception;
        }
        return callerFuture;
    }

    @Override
    public void invalidateRevision(long gridScope, long currentRevision) {
        if (gridScope < 0L || currentRevision < 0L) {
            throw new IllegalArgumentException("Trinity revision invalidation requires non-negative scope and revision");
        }
        ObjectList<CacheEntry<?>> cancelled = new ObjectArrayList<>();
        synchronized (this.cacheLock) {
            requireOpen();
            GridPartition partition = this.partitions.computeIfAbsent(gridScope, GridPartition::new);
            advanceRevision(
                    partition,
                    TrinityComputationNamespace.REQUEST_IN_FLIGHT,
                    currentRevision,
                    cancelled);
        }
        cancelled.forEach(CacheEntry::cancelObsolete);
    }

    @Override
    public void clearGrid(long gridScope) {
        if (gridScope < 0L) {
            throw new IllegalArgumentException("A Trinity Grid scope must be non-negative");
        }
        List<CacheEntry<?>> cancelled;
        synchronized (this.cacheLock) {
            requireOpen();
            if (!this.partitions.containsKey(gridScope)) {
                return;
            }
            GridPartition partition = this.partitions.remove(gridScope);
            cancelled = partition.allEntries();
            cancelled.forEach(CacheEntry::markCancellation);
            partition.clear();
        }
        cancelled.forEach(CacheEntry::cancelUnderlying);
    }

    @Override
    public void close() {
        ObjectList<CacheEntry<?>> cancelled = new ObjectArrayList<>();
        synchronized (this.cacheLock) {
            if (this.closed) {
                return;
            }
            this.closed = true;
            for (GridPartition partition : this.partitions.values()) {
                List<CacheEntry<?>> partitionEntries = partition.allEntries();
                partitionEntries.forEach(CacheEntry::markCancellation);
                cancelled.addAll(partitionEntries);
                partition.clear();
            }
            this.partitions.clear();
        }
        cancelled.forEach(CacheEntry::cancelUnderlying);
    }

    private static void validateKey(
                                    long gridScope,
                                    TrinityComputationNamespace namespace,
                                    long revision) {
        if (gridScope < 0L) {
            throw new IllegalArgumentException("A Trinity computation cache requires a non-negative Grid scope");
        }
        if (namespace.revisionBound() && revision < 0L) {
            throw new IllegalArgumentException("A revision-bound Trinity computation requires a publication revision");
        }
        if (!namespace.revisionBound() && revision != SEMANTIC_REVISION) {
            throw new IllegalArgumentException("A semantic Trinity computation must use the semantic revision marker");
        }
    }

    private boolean reserveRegisteredSlot(GridPartition partition) {
        if (partition.entries.size() < this.gridEntryLimit) {
            return true;
        }
        Iterator<Map.Entry<ScopedKey, CacheEntry<?>>> entries = partition.entries.entrySet().iterator();
        while (entries.hasNext()) {
            if (entries.next().getValue().result.isDone()) {
                entries.remove();
                return true;
            }
        }
        return false;
    }

    private static void removeObsoleteEntries(
                                              Iterator<Map.Entry<ScopedKey, CacheEntry<?>>> entries,
                                              TrinityComputationNamespace.RevisionDomain revisionDomain,
                                              long currentRevision,
                                              List<CacheEntry<?>> cancelled) {
        while (entries.hasNext()) {
            Map.Entry<ScopedKey, CacheEntry<?>> mapped = entries.next();
            if (isObsolete(mapped.getKey(), revisionDomain, currentRevision)) {
                CacheEntry<?> obsolete = mapped.getValue();
                entries.remove();
                obsolete.markCancellation();
                cancelled.add(obsolete);
            }
        }
    }

    private static boolean isObsolete(ScopedKey key,
                                      TrinityComputationNamespace.RevisionDomain revisionDomain,
                                      long currentRevision) {
        return key.namespace().revisionDomain() == revisionDomain && key.revision() < currentRevision;
    }

    private static boolean advanceRevision(
                                           GridPartition partition,
                                           TrinityComputationNamespace namespace,
                                           long revision,
                                           List<CacheEntry<?>> cancelled) {
        if (!namespace.revisionBound()) {
            return false;
        }
        TrinityComputationNamespace.RevisionDomain revisionDomain = namespace.revisionDomain();
        long currentRevision = partition.currentRevisions.getOrDefault(revisionDomain, -1L);
        if (revision < currentRevision) {
            return true;
        }
        if (revision == currentRevision) {
            return false;
        }
        partition.currentRevisions.put(revisionDomain, revision);
        removeObsoleteEntries(partition.entries.entrySet().iterator(), revisionDomain, revision, cancelled);
        Iterator<Map.Entry<ScopedKey, CacheEntry<?>>> bypass = partition.bypassEntries.entrySet().iterator();
        while (bypass.hasNext()) {
            Map.Entry<ScopedKey, CacheEntry<?>> mapped = bypass.next();
            if (isObsolete(mapped.getKey(), revisionDomain, revision)) {
                CacheEntry<?> obsolete = mapped.getValue();
                bypass.remove();
                obsolete.markCancellation();
                cancelled.add(obsolete);
            }
        }
        return false;
    }

    private void removeEmptyPartition(long gridScope, GridPartition partition) {
        if (partition.currentRevisions.isEmpty() && partition.entries.isEmpty() && partition.bypassEntries.isEmpty()) {
            this.partitions.remove(gridScope, partition);
        }
    }

    private void requireOpen() {
        if (this.closed) {
            throw new IllegalStateException("The Trinity computation cache is closed");
        }
    }

    private final class CallerFuture<V> implements Future<V> {

        private final SubscriberLease<V> lease;
        private final CompletableFuture<V> delegate = new CompletableFuture<>();

        private CallerFuture(CacheEntry<V> entry) {
            this.lease = new SubscriberLease<>(entry);
            entry.result.whenComplete((value, failure) -> {
                if (failure == null) {
                    this.delegate.complete(value);
                } else if (failure instanceof CancellationException) {
                    this.delegate.cancel(false);
                } else {
                    this.delegate.completeExceptionally(failure);
                }
                this.lease.release(false);
            });
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (!this.delegate.cancel(false)) {
                return false;
            }
            this.lease.release(mayInterruptIfRunning);
            return true;
        }

        @Override
        public boolean isCancelled() {
            return this.delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return this.delegate.isDone();
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            return this.delegate.get();
        }

        @Override
        public V get(long timeout, TimeUnit unit)
                                                  throws InterruptedException, ExecutionException, TimeoutException {
            return this.delegate.get(timeout, unit);
        }
    }

    private final class SubscriberLease<V> {

        private final CacheEntry<V> entry;
        private boolean released;

        private SubscriberLease(CacheEntry<V> entry) {
            this.entry = entry;
            this.entry.subscribers++;
        }

        private boolean release(boolean interrupt) {
            CancellationAction action = null;
            synchronized (BoundedTrinityComputationCache.this.cacheLock) {
                if (this.released) {
                    return false;
                }
                this.released = true;
                this.entry.subscribers--;
                if (this.entry.subscribers < 0) {
                    throw new IllegalStateException("A Trinity computation subscriber was released more than once");
                }
                if (this.entry.subscribers == 0 && this.entry.state.compareAndSet(CacheEntryState.ACTIVE, CacheEntryState.CANCELLED)) {
                    this.entry.removeFromPartition();
                    action = this.entry.cancellationAction(interrupt);
                }
            }
            if (action != null) {
                action.execute();
                return true;
            }
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static <V> CacheEntry<V> castEntry(CacheEntry<?> entry) {
        return (CacheEntry<V>) entry;
    }

    private final class CacheEntry<V> {

        private final GridPartition partition;
        private final ScopedKey key;
        private final Callable<TrinityCachedComputation<V>> calculation;
        private final boolean registered;
        private final boolean revisionCancellationInterrupts;
        private final AtomicReference<CacheEntryState> state = new AtomicReference<>(CacheEntryState.ACTIVE);
        private final CompletableFuture<V> result = new CompletableFuture<>();
        private final FutureTask<Void> execution = new FutureTask<>(this::execute);
        private int subscribers;
        private @Nullable SubscriberLease<?> inlineLease;
        private boolean inlineOwner;

        private CacheEntry(GridPartition partition,
                           ScopedKey key,
                           Callable<TrinityCachedComputation<V>> calculation,
                           boolean registered,
                           boolean revisionCancellationInterrupts) {
            this.partition = partition;
            this.key = key;
            this.calculation = calculation;
            this.registered = registered;
            this.revisionCancellationInterrupts = revisionCancellationInterrupts;
        }

        private Void execute() {
            CacheEntry<?> previousInlineOwner = BoundedTrinityComputationCache.this.inlineOwnerEntry.get();
            BoundedTrinityComputationCache.this.inlineOwnerEntry.set(this);
            try {
                TrinityCachedComputation<V> computed = this.calculation.call();
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("The owning Trinity planning request was cancelled");
                }
                publish(computed);
                return null;
            } catch (Error error) {
                fail(error);
                throw error;
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                fail(exception);
                return null;
            } finally {
                if (previousInlineOwner == null) {
                    BoundedTrinityComputationCache.this.inlineOwnerEntry.remove();
                } else {
                    BoundedTrinityComputationCache.this.inlineOwnerEntry.set(previousInlineOwner);
                }
            }
        }

        private void publish(TrinityCachedComputation<V> computed) {
            if (!this.state.compareAndSet(CacheEntryState.ACTIVE, CacheEntryState.PUBLISHED)) {
                return;
            }
            if (!computed.cacheable() || !this.registered) {
                synchronized (BoundedTrinityComputationCache.this.cacheLock) {
                    removeFromPartition();
                }
            }
            this.result.complete(computed.value());
        }

        private void fail(Throwable failure) {
            if (!this.state.compareAndSet(CacheEntryState.ACTIVE, CacheEntryState.PUBLISHED)) {
                return;
            }
            synchronized (BoundedTrinityComputationCache.this.cacheLock) {
                removeFromPartition();
            }
            this.result.completeExceptionally(failure);
        }

        private void reject(RejectedExecutionException failure) {
            fail(failure);
            this.execution.cancel(false);
        }

        private void removeFromPartition() {
            if (this.registered) {
                this.partition.entries.remove(this.key, this);
            } else {
                this.partition.bypassEntries.remove(this.key, this);
            }
            removeEmptyPartition(this.partition.gridScope, this.partition);
        }

        private void requireInlineAdmission() {
            if (this.state.get() != CacheEntryState.ACTIVE) {
                throw new CancellationException("The owning Trinity planning request was cancelled");
            }
            if (this.inlineLease != null) {
                throw new IllegalStateException("A Trinity planning request cannot execute two inline cache layers at once");
            }
        }

        private void attachInline(SubscriberLease<?> lease, boolean owner) {
            this.inlineLease = lease;
            this.inlineOwner = owner;
        }

        private void clearInline(SubscriberLease<?> lease) {
            if (this.inlineLease == lease) {
                this.inlineLease = null;
                this.inlineOwner = false;
            }
        }

        private void cancelUnderlying() {
            finishCancellation(true);
        }

        private void cancelObsolete() {
            finishCancellation(this.revisionCancellationInterrupts);
        }

        private void markCancellation() {
            this.state.compareAndSet(CacheEntryState.ACTIVE, CacheEntryState.CANCELLED);
        }

        private void finishCancellation(boolean interrupt) {
            CancellationAction action;
            synchronized (BoundedTrinityComputationCache.this.cacheLock) {
                markCancellation();
                action = cancellationAction(interrupt);
            }
            action.execute();
        }

        private CancellationAction cancellationAction(boolean interrupt) {
            SubscriberLease<?> childLease = this.inlineLease;
            boolean childOwner = this.inlineOwner;
            this.inlineLease = null;
            this.inlineOwner = false;
            return new CancellationAction(
                    this,
                    childLease,
                    childOwner,
                    interrupt,
                    this.state.get() == CacheEntryState.CANCELLED);
        }
    }

    private final class CancellationAction {

        private final CacheEntry<?> entry;
        private final @Nullable SubscriberLease<?> childLease;
        private final boolean childOwner;
        private final boolean interrupt;
        private final boolean cancelResult;

        private CancellationAction(
                                   CacheEntry<?> entry,
                                   @Nullable SubscriberLease<?> childLease,
                                   boolean childOwner,
                                   boolean interrupt,
                                   boolean cancelResult) {
            this.entry = entry;
            this.childLease = childLease;
            this.childOwner = childOwner;
            this.interrupt = interrupt;
            this.cancelResult = cancelResult;
        }

        private void execute() {
            if (this.cancelResult) {
                this.entry.result.cancel(false);
            }
            boolean childCancelled = this.childLease != null && this.childLease.release(this.interrupt);
            if (!this.interrupt) {
                this.entry.execution.cancel(false);
                return;
            }
            if (this.childLease == null || !this.childOwner || childCancelled || this.childLease.entry.state.get() != CacheEntryState.ACTIVE) {
                this.entry.execution.cancel(true);
            }
        }
    }

    private enum CacheEntryState {
        ACTIVE,
        PUBLISHED,
        CANCELLED
    }

    private static final class GridPartition {

        private final long gridScope;
        private final Object2ObjectLinkedOpenHashMap<ScopedKey, CacheEntry<?>> entries = new Object2ObjectLinkedOpenHashMap<>();
        private final Object2ObjectOpenHashMap<ScopedKey, CacheEntry<?>> bypassEntries = new Object2ObjectOpenHashMap<>();
        private final Map<TrinityComputationNamespace.RevisionDomain, Long> currentRevisions = new EnumMap<>(TrinityComputationNamespace.RevisionDomain.class);

        private GridPartition(long gridScope) {
            this.gridScope = gridScope;
        }

        @Nullable
        private CacheEntry<?> registeredEntry(ScopedKey key) {
            if (!this.entries.containsKey(key)) {
                return null;
            }
            return this.entries.getAndMoveToLast(key);
        }

        @Nullable
        private CacheEntry<?> bypassEntry(ScopedKey key) {
            if (!this.bypassEntries.containsKey(key)) {
                return null;
            }
            return this.bypassEntries.get(key);
        }

        private ObjectList<CacheEntry<?>> allEntries() {
            ObjectArrayList<CacheEntry<?>> all = new ObjectArrayList<>(this.entries.values());
            all.addAll(this.bypassEntries.values());
            return all;
        }

        private void clear() {
            this.entries.clear();
            this.bypassEntries.clear();
            this.currentRevisions.clear();
        }
    }

    private record ScopedKey(TrinityComputationNamespace namespace, long revision, Object value) {}
}
