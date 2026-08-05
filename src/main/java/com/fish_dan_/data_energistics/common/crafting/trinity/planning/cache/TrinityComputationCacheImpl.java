package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        synchronized (this.cacheLock) {
            requireOpen();
            partition = this.partitions.computeIfAbsent(gridScope, GridPartition::new);
            ScopedKey scopedKey = new ScopedKey(namespace, revision, key);
            CacheEntry<?> existing = partition.entries.get(scopedKey);
            if (existing != null) {
                return lookup(castEntry(existing), true, true);
            }

            registered = reserveRegisteredSlot(partition);
            entry = new CacheEntry<>(partition, scopedKey, calculation, registered);
            if (registered) {
                partition.entries.put(scopedKey, entry);
            } else {
                partition.bypassEntries.add(entry);
            }
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
        synchronized (this.cacheLock) {
            requireOpen();
            GridPartition partition = this.partitions.computeIfAbsent(gridScope, GridPartition::new);
            ScopedKey scopedKey = new ScopedKey(namespace, revision, key);
            CacheEntry<?> existing = partition.entries.get(scopedKey);
            if (existing != null) {
                entry = castEntry(existing);
                cacheHit = true;
                registered = true;
            } else {
                cacheHit = false;
                registered = reserveRegisteredSlot(partition);
                entry = new CacheEntry<>(partition, scopedKey, calculation, registered);
                if (registered) {
                    partition.entries.put(scopedKey, entry);
                } else {
                    partition.bypassEntries.add(entry);
                }
            }
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
        synchronized (this.cacheLock) {
            requireOpen();
            GridPartition partition = this.partitions.computeIfAbsent(gridScope, GridPartition::new);
            entry = new CacheEntry<>(
                    partition,
                    new ScopedKey(TrinityComputationNamespace.SOLVED_PLAN, revision, new Object()),
                    () -> TrinityCachedComputation.transientValue(calculation.call()),
                    false);
            partition.bypassEntries.add(entry);
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
            GridPartition partition = this.partitions.get(gridScope);
            if (partition == null) {
                return;
            }
            removeObsoleteEntries(partition.entries.entrySet().iterator(), currentRevision, cancelled);
            Iterator<CacheEntry<?>> bypass = partition.bypassEntries.iterator();
            while (bypass.hasNext()) {
                CacheEntry<?> entry = bypass.next();
                if (isObsolete(entry.key, currentRevision)) {
                    bypass.remove();
                    cancelled.add(entry);
                }
            }
            removeEmptyPartition(gridScope, partition);
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
            if (!entries.next().getValue().inFlight) {
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
        return key.namespace().revisionBound() && key.revision() != currentRevision;
    }

    private void removeEmptyPartition(long gridScope, GridPartition partition) {
        if (partition.entries.isEmpty() && partition.bypassEntries.isEmpty()) {
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
        private boolean inFlight = true;

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
            this.result.complete(computed.value());
            synchronized (TrinityComputationCacheImpl.this.cacheLock) {
                this.inFlight = false;
                if (!this.registered || !computed.cacheable()) {
                    removeFromPartition();
                }
            }
        }

        private void fail(Throwable failure) {
            this.result.completeExceptionally(failure);
            synchronized (TrinityComputationCacheImpl.this.cacheLock) {
                this.inFlight = false;
                removeFromPartition();
            }
        }

        private void reject(RejectedExecutionException failure) {
            fail(failure);
            this.execution.cancel(false);
        }

        private void removeFromPartition() {
            if (this.registered) {
                this.partition.entries.remove(this.key, this);
            } else {
                this.partition.bypassEntries.remove(this);
            }
            removeEmptyPartition(this.partition.gridScope, this.partition);
        }

        private void cancelUnderlying() {
            this.result.cancel(false);
            this.execution.cancel(true);
            synchronized (TrinityComputationCacheImpl.this.cacheLock) {
                this.inFlight = false;
            }
        }
    }

    private static final class GridPartition {

        private final long gridScope;
        private final LinkedHashMap<ScopedKey, CacheEntry<?>> entries = new LinkedHashMap<>(16, 0.75F, true);
        private final Set<CacheEntry<?>> bypassEntries = Collections.newSetFromMap(new IdentityHashMap<>());

        private GridPartition(long gridScope) {
            this.gridScope = gridScope;
        }

        private List<CacheEntry<?>> allEntries() {
            ArrayList<CacheEntry<?>> all = new ArrayList<>(this.entries.values());
            all.addAll(this.bypassEntries);
            return List.copyOf(all);
        }

        private void clear() {
            this.entries.clear();
            this.bypassEntries.clear();
        }
    }

    private record ScopedKey(TrinityComputationNamespace namespace, long revision, Object value) {}
}
