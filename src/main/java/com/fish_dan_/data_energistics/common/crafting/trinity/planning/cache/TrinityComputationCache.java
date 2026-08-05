package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * Shares immutable Trinity calculations within a server lifetime without sharing caller cancellation.
 */
public interface TrinityComputationCache extends AutoCloseable {

    /** Revision marker required by semantic namespaces that can cross publication revisions. */
    long SEMANTIC_REVISION = -1L;

    /** Default maximum number of registered entries across all namespaces for one Grid scope. */
    int DEFAULT_GRID_ENTRY_LIMIT = 4096;

    /**
     * Creates a cache that submits bottom-level calculations to an existing bounded executor.
     *
     * @param executor server-lifetime planner or proposal executor
     * @return independently owned computation cache
     */
    static TrinityComputationCache create(Executor executor) {
        return new TrinityComputationCacheImpl(executor, DEFAULT_GRID_ENTRY_LIMIT);
    }

    /**
     * Creates a cache with an explicit per-Grid entry limit for focused verification.
     *
     * @param executor       server-lifetime bounded executor
     * @param gridEntryLimit maximum registered entries across all namespaces for one Grid
     * @return independently owned computation cache
     */
    static TrinityComputationCache create(Executor executor, int gridEntryLimit) {
        return new TrinityComputationCacheImpl(executor, gridEntryLimit);
    }

    /**
     * Reuses or starts one pure computation and returns an isolated caller wait handle.
     *
     * @param gridScope   immutable Grid publication scope
     * @param namespace   computation namespace
     * @param revision    publication revision, or {@link #SEMANTIC_REVISION} for semantic namespaces
     * @param key         complete immutable semantic key
     * @param calculation bottom-level pure calculation
     * @param <K>         key type
     * @param <V>         result type
     * @return cache lookup and caller-owned future
     */
    <K, V> TrinityComputationLookup<V> compute(
                                                  long gridScope,
                                                  TrinityComputationNamespace namespace,
                                                  long revision,
                                                  K key,
                                                  Callable<TrinityCachedComputation<V>> calculation);

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
