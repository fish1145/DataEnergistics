package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

/**
 * Shares immutable Trinity calculations while they have active subscribers, retains completed values within a server
 * lifetime, and removes unfinished work when its final request is cancelled.
 */
public interface TrinityComputationCache extends AutoCloseable {

    /**
     * Revision marker required by semantic namespaces that can cross publication revisions.
     */
    long SEMANTIC_REVISION = -1L;

    /**
     * Default maximum number of registered entries across all namespaces for one Grid scope.
     */
    int DEFAULT_GRID_ENTRY_LIMIT = 4096;

    /**
     * Creates a cache that submits bottom-level calculations to an existing bounded executor.
     *
     * @param executor server-lifetime planner or proposal executor
     * @return independently owned computation cache
     */
    static TrinityComputationCache create(Executor executor) {
        return new BoundedTrinityComputationCache(executor, DEFAULT_GRID_ENTRY_LIMIT);
    }

    /**
     * Creates a cache with an explicit per-Grid entry limit for focused verification.
     *
     * @param executor       server-lifetime bounded executor
     * @param gridEntryLimit maximum registered entries across all namespaces for one Grid
     * @return independently owned computation cache
     */
    static TrinityComputationCache create(Executor executor, int gridEntryLimit) {
        return new BoundedTrinityComputationCache(executor, gridEntryLimit);
    }

    /**
     * Runs the cache-owning calculation on the current cache-managed worker. Concurrent callers may share one active
     * inline calculation, and completed results remain reusable. Subscriber tracking cancels the active calculation
     * once no request still needs it, without nested submission to the same bounded executor.
     *
     * @param gridScope   immutable Grid publication scope
     * @param namespace   computation namespace
     * @param revision    publication revision, or {@link #SEMANTIC_REVISION} for semantic namespaces
     * @param key         complete immutable semantic key
     * @param calculation bottom-level pure calculation executed only by the active miss owner
     * @param <K>         key type
     * @param <V>         result type
     * @return immutable value and cache-selection metadata
     * @throws InterruptedException when this worker is interrupted while waiting on another cache owner
     * @throws ExecutionException   when the bottom calculation fails
     */
    default <K, V> TrinityComputationValue<V> computeInline(
                                                            long gridScope,
                                                            TrinityComputationNamespace namespace,
                                                            long revision,
                                                            K key,
                                                            Callable<TrinityCachedComputation<V>> calculation)
                                                                                                               throws InterruptedException, ExecutionException {
        return computeInlineIfActive(
                gridScope,
                namespace,
                revision,
                key,
                () -> true,
                calculation).orElseThrow(
                        () -> new IllegalStateException(
                                "An unconditional Trinity inline computation was not admitted"));
    }

    /**
     * Atomically checks caller lifecycle and joins or creates an inline cache entry under the cache lock. The guard
     * prevents work that outlives an unloaded Grid from recreating its cleared partition.
     *
     * @param gridScope       immutable Grid publication scope
     * @param namespace       computation namespace
     * @param revision        publication revision, or {@link #SEMANTIC_REVISION} for semantic namespaces
     * @param key             complete immutable semantic key
     * @param lifecycleActive true while the caller may still publish work for this Grid
     * @param calculation     bottom-level pure calculation executed only by the active miss owner
     * @param <K>             key type
     * @param <V>             result type
     * @return empty when lifecycle admission is denied, otherwise immutable value and cache-selection metadata
     * @throws InterruptedException when this worker is interrupted while waiting on another cache owner
     * @throws ExecutionException   when the bottom calculation fails
     */
    <K, V> Optional<TrinityComputationValue<V>> computeInlineIfActive(
                                                                      long gridScope,
                                                                      TrinityComputationNamespace namespace,
                                                                      long revision,
                                                                      K key,
                                                                      BooleanSupplier lifecycleActive,
                                                                      Callable<TrinityCachedComputation<V>> calculation)
                                                                                                                         throws InterruptedException, ExecutionException;

    /**
     * Reads one completed retained value without creating or joining an active computation.
     */
    <K, V> Optional<V> getIfPresent(
                                    long gridScope,
                                    TrinityComputationNamespace namespace,
                                    long revision,
                                    K key);

    /**
     * Atomically publishes one already verified immutable value when no retained or active entry owns the key.
     *
     */
    <K, V> void publishIfAbsent(
                                long gridScope,
                                TrinityComputationNamespace namespace,
                                long revision,
                                K key,
                                V value);

    /**
     * Submits one lifecycle-tracked orchestration task on the cache's default executor without registering it in the
     * LRU. The returned future owns the orchestration, while completed inner values remain globally reusable.
     *
     * @param gridScope   immutable Grid publication scope
     * @param calculation orchestration that enters inline cache layers
     * @param <V>         result type
     * @return caller-owned future
     */
    <V> Future<V> submit(long gridScope, Callable<V> calculation);

    /**
     * Submits one lifecycle-tracked orchestration task on a selected execution lane. Lane selection does not partition
     * cached values: every inline layer still joins this cache's global thread-safe Grid partition.
     *
     * @param executionLane executor reserved for this request family
     * @param gridScope     immutable Grid publication scope
     * @param calculation   orchestration that enters inline cache layers
     * @param <V>           result type
     * @return caller-owned future
     */
    <V> Future<V> submit(Executor executionLane, long gridScope, Callable<V> calculation);

    /**
     * Removes and cancels revision-bound entries that cannot publish into the current graph revision.
     *
     * @param gridScope       Grid partition to invalidate
     * @param currentRevision currently published graph revision
     */
    void invalidateRevision(long gridScope, long currentRevision);

    /**
     * Cancels in-flight calculations and removes every namespace for an unloaded Grid.
     *
     * @param gridScope unloaded Grid publication scope
     */
    void clearGrid(long gridScope);

    /**
     * Cancels all shared and bypass calculations and permanently closes this server-lifetime cache.
     */
    @Override
    void close();
}
