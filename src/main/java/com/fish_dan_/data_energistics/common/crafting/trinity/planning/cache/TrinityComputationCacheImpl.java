package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;

/**
 * Access-order LRU implementation with pinned in-flight entries and lifecycle-aware bypass tasks.
 */
final class TrinityComputationCacheImpl implements TrinityComputationCache {

    private final Object cacheLock = new Object();
    private final Executor executor;
    private final int gridEntryLimit;
    private final Map<Long, GridPartition> partitions = new HashMap<>();
    private boolean closed;

    TrinityComputationCacheImpl(Executor executor, int gridEntryLimit) {
        if (executor == null) {
            throw new IllegalArgumentException("A Trinity computation cache requires an executor");
        }
        if (gridEntryLimit <= 0) {
            throw new IllegalArgumentException("A Trinity computation cache requires a positive Grid entry limit");
        }
        this.executor = executor;
        this.gridEntryLimit = gridEntryLimit;
    }

    @Override
    public <K, V> TrinityComputationLookup<V> compute(
                                                          long gridScope,
                                                          TrinityComputationNamespace namespace,
                                                          long revision,
                                                          K key,
                                                          Callable<TrinityCachedComputation<V>> calculation) {
        validateRequest(gridScope, namespace, revision, key, calculation);

        GridPartition partition;
        CacheEntry<V> entry;
        boolean registered;
        TrinityComputationLookup<V> existingLookup = null;
        List<CacheEntry<?>> cancelled = new ArrayList<>();
        synchronized (this.cacheLock) {
            requireOpen();
            partition = this.partitions.computeIfAbsent(gridScope, GridPartition::new);
            ScopedKey scopedKey = new ScopedKey(namespace, revision, key);
            boolean staleRevision = advanceRevision(partition, namespace, revision, cancelled);
            CacheEntry<?> existing = partition.entries.get(scopedKey);
            boolean existingRegistered = true;
            if (existing == null) {
                existing = partition.bypassEntries.get(scopedKey);
                existingRegistered = false;
            }
            if (existing != null) {
                existingLookup = lookup(castEntry(existing), true, existingRegistered);
                entry = null;
                registered = existingRegistered;
            } else if (staleRevision) {
                CompletableFuture<V> stale = new CompletableFuture<>();
                stale.cancel(false);
                existingLookup = new TrinityComputationLookup<>(stale, false, false);
                entry = null;
                registered = false;
            } else {
                registered = reserveRegisteredSlot(partition);
                entry = new CacheEntry<>(partition, scopedKey, calculation, registered);
                if (registered) {
                    partition.entries.put(scopedKey, entry);
                } else {
                    partition.bypassEntries.put(scopedKey, entry);
                }
            }
        }
        cancelled.forEach(CacheEntry::cancelUnderlying);
        if (existingLookup != null) {
            return existingLookup;
        }

        try {
            this.executor.execute(entry.execution);
        } catch (RejectedExecutionException exception) {
            entry.reject(exception);
            throw exception;
        }
        return lookup(entry, false, registered);
    }

    @Override
    public <K, V> TrinityComputationValue<V> computeInline(
                                                              long gridScope,
                                                              TrinityComputationNamespace namespace,
                                                              long revision,
                                                              K key,
                                                              Callable<TrinityCachedComputation<V>> calculation)
            throws InterruptedException, ExecutionException {
        validateRequest(gridScope, namespace, revision, key, calculation);

        CacheEntry<V> entry;
        boolean cacheHit;
        boolean registered;
        boolean staleRevision;
        List<CacheEntry<?>> cancelled = new ArrayList<>();
        synchronized (this.cacheLock) {
            requireOpen();
            GridPartition partition = this.partitions.computeIfAbsent(gridScope, GridPartition::new);
            ScopedKey scopedKey = new ScopedKey(namespace, revision, key);
            staleRevision = advanceRevision(partition, namespace, revision, cancelled);
            CacheEntry<?> existing = partition.entries.get(scopedKey);
            boolean existingRegistered = true;
            if (existing == null) {
                existing = partition.bypassEntries.get(scopedKey);
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
                entry = new CacheEntry<>(partition, scopedKey, calculation, registered);
                if (registered) {
                    partition.entries.put(scopedKey, entry);
                } else {
                    partition.bypassEntries.put(scopedKey, entry);
                }
            }
        }
        cancelled.forEach(CacheEntry::cancelUnderlying);

        if (staleRevision && entry == null) {
            throw new CancellationException("The Trinity computation revision is obsolete");
        }

        if (!cacheHit) {
            entry.execution.run();
        }
        try {
            return new TrinityComputationValue<>(entry.result.get(), cacheHit, registered);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    @Override
    public <V> Future<V> submit(long gridScope, long revision, Callable<V> calculation) {
        if (gridScope < 0L || revision < 0L || calculation == null) {
            throw new IllegalArgumentException("A detached Trinity computation requires scope, revision, and calculation");
        }
        CacheEntry<V> entry;
        List<CacheEntry<?>> cancelled = new ArrayList<>();
        boolean staleRevision;
        synchronized (this.cacheLock) {
            requireOpen();
            GridPartition partition = this.partitions.computeIfAbsent(gridScope, GridPartition::new);
            staleRevision = advanceRevision(
                    partition,
                    TrinityComputationNamespace.SOLVED_PLAN,
                    revision,
                    cancelled);
            if (staleRevision) {
                entry = null;
            } else {
                ScopedKey scopedKey = new ScopedKey(TrinityComputationNamespace.SOLVED_PLAN, revision, new Object());
                entry = new CacheEntry<>(
                        partition,
                        scopedKey,
                        () -> TrinityCachedComputation.transientValue(calculation.call()),
                        false);
                partition.bypassEntries.put(scopedKey, entry);
            }
        }
        cancelled.forEach(CacheEntry::cancelUnderlying);
        if (staleRevision) {
            CompletableFuture<V> stale = new CompletableFuture<>();
            stale.cancel(false);
            return stale;
        }
        try {
            this.executor.execute(entry.execution);
        } catch (RejectedExecutionException exception) {
            entry.reject(exception);
            throw exception;
        }
        return lookup(entry, false, false).future();
    }

    @Override
    public void invalidateRevision(long gridScope, long currentRevision) {
        if (gridScope < 0L || currentRevision < 0L) {
            throw new IllegalArgumentException("Trinity revision invalidation requires non-negative scope and revision");
        }
        List<CacheEntry<?>> cancelled = new ArrayList<>();
        synchronized (this.cacheLock) {
            requireOpen();
            GridPartition partition = this.partitions.computeIfAbsent(gridScope, GridPartition::new);
            advanceRevision(
                    partition,
                    TrinityComputationNamespace.SOLVED_PLAN,
                    currentRevision,
                    cancelled);
        }
        cancelled.forEach(CacheEntry::cancelUnderlying);
    }

    @Override
    public void clearGrid(long gridScope) {
        if (gridScope < 0L) {
            throw new IllegalArgumentException("A Trinity Grid scope must be non-negative");
        }
        List<CacheEntry<?>> cancelled;
        synchronized (this.cacheLock) {
            requireOpen();
            GridPartition partition = this.partitions.remove(gridScope);
            if (partition == null) {
                return;
            }
            cancelled = partition.allEntries();
            partition.clear();
        }
        cancelled.forEach(CacheEntry::cancelUnderlying);
    }

    @Override
    public void close() {
        List<CacheEntry<?>> cancelled = new ArrayList<>();
        synchronized (this.cacheLock) {
            if (this.closed) {
                return;
            }
            this.closed = true;
            for (GridPartition partition : this.partitions.values()) {
                cancelled.addAll(partition.allEntries());
                partition.clear();
            }
            this.partitions.clear();
        }
        cancelled.forEach(CacheEntry::cancelUnderlying);
    }

    private static <K, V> void validateRequest(
                                                  long gridScope,
                                                  TrinityComputationNamespace namespace,
                                                  long revision,
                                                  K key,
                                                  Callable<TrinityCachedComputation<V>> calculation) {
        if (gridScope < 0L || namespace == null || key == null || calculation == null) {
            throw new IllegalArgumentException("A Trinity computation cache request is incomplete");
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
                                                 long currentRevision,
                                                 List<CacheEntry<?>> cancelled) {
        while (entries.hasNext()) {
            Map.Entry<ScopedKey, CacheEntry<?>> mapped = entries.next();
            if (isObsolete(mapped.getKey(), currentRevision)) {
                entries.remove();
                cancelled.add(mapped.getValue());
            }
        }
    }

    private static boolean isObsolete(ScopedKey key, long currentRevision) {
        return key.namespace().revisionBound() && key.revision() < currentRevision;
    }

    private static boolean advanceRevision(
                                           GridPartition partition,
                                           TrinityComputationNamespace namespace,
                                           long revision,
                                           List<CacheEntry<?>> cancelled) {
        if (!namespace.revisionBound()) {
            return false;
        }
        if (revision < partition.currentRevision) {
            return true;
        }
        if (revision == partition.currentRevision) {
            return false;
        }
        partition.currentRevision = revision;
        removeObsoleteEntries(partition.entries.entrySet().iterator(), revision, cancelled);
        Iterator<Map.Entry<ScopedKey, CacheEntry<?>>> bypass = partition.bypassEntries.entrySet().iterator();
        while (bypass.hasNext()) {
            Map.Entry<ScopedKey, CacheEntry<?>> mapped = bypass.next();
            if (isObsolete(mapped.getKey(), revision)) {
                bypass.remove();
                cancelled.add(mapped.getValue());
            }
        }
        return false;
    }

    private void removeEmptyPartition(long gridScope, GridPartition partition) {
        if (partition.currentRevision < 0L && partition.entries.isEmpty() && partition.bypassEntries.isEmpty()) {
            this.partitions.remove(gridScope, partition);
        }
    }

    private void requireOpen() {
        if (this.closed) {
            throw new IllegalStateException("The Trinity computation cache is closed");
        }
    }

    private static <V> TrinityComputationLookup<V> lookup(
                                                            CacheEntry<V> entry,
                                                            boolean cacheHit,
                                                            boolean registered) {
        CompletableFuture<V> callerFuture = new CompletableFuture<>();
        entry.result.whenComplete((value, failure) -> {
            if (failure == null) {
                callerFuture.complete(value);
            } else if (failure instanceof CancellationException) {
                callerFuture.cancel(false);
            } else {
                callerFuture.completeExceptionally(failure);
            }
        });
        return new TrinityComputationLookup<>(callerFuture, cacheHit, registered);
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
        private final CompletableFuture<V> result = new CompletableFuture<>();
        private final FutureTask<Void> execution = new FutureTask<>(this::execute);

        private CacheEntry(GridPartition partition,
                           ScopedKey key,
                           Callable<TrinityCachedComputation<V>> calculation,
                           boolean registered) {
            this.partition = partition;
            this.key = key;
            this.calculation = calculation;
            this.registered = registered;
        }

        private Void execute() {
            try {
                TrinityCachedComputation<V> computed = this.calculation.call();
                if (computed == null) {
                    throw new IllegalStateException("A Trinity computation returned no publication result");
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
            }
        }

        private void publish(TrinityCachedComputation<V> computed) {
            if (!computed.cacheable()) {
                synchronized (TrinityComputationCacheImpl.this.cacheLock) {
                    removeFromPartition();
                }
                this.result.complete(computed.value());
                return;
            }
            this.result.complete(computed.value());
            if (!this.registered) {
                synchronized (TrinityComputationCacheImpl.this.cacheLock) {
                    removeFromPartition();
                }
            }
        }

        private void fail(Throwable failure) {
            synchronized (TrinityComputationCacheImpl.this.cacheLock) {
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

        private void cancelUnderlying() {
            this.result.cancel(false);
            this.execution.cancel(true);
        }
    }

    private static final class GridPartition {

        private final long gridScope;
        private final LinkedHashMap<ScopedKey, CacheEntry<?>> entries = new LinkedHashMap<>(16, 0.75F, true);
        private final Map<ScopedKey, CacheEntry<?>> bypassEntries = new HashMap<>();
        private long currentRevision = -1L;

        private GridPartition(long gridScope) {
            this.gridScope = gridScope;
        }

        private List<CacheEntry<?>> allEntries() {
            ArrayList<CacheEntry<?>> all = new ArrayList<>(this.entries.values());
            all.addAll(this.bypassEntries.values());
            return List.copyOf(all);
        }

        private void clear() {
            this.entries.clear();
            this.bypassEntries.clear();
        }
    }

    private record ScopedKey(TrinityComputationNamespace namespace, long revision, Object value) {}
}
