package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.lifecycle;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;

import org.jspecify.annotations.Nullable;

/**
 * Owns the bounded proposal executor and its dispatch-only computation cache for one logical server lifetime.
 */
public final class TrinityDispatchProposalLifecycle {

    private static final Object LIFECYCLE_LOCK = new Object();
    private static volatile @Nullable DispatchResources resources;

    private TrinityDispatchProposalLifecycle() {}

    /** Starts the dispatch executor after COMMON configuration has loaded. */
    public static void start() {
        synchronized (LIFECYCLE_LOCK) {
            if (resources != null) {
                throw new IllegalStateException("The Trinity dispatch proposal scheduler is already running");
            }
            TrinityComputationCache dispatchCache = TrinityComputationCache.create(Runnable::run);
            try {
                DispatchProposalScheduler scheduler = DispatchProposalScheduler.create(() -> dispatchCache);
                resources = new DispatchResources(dispatchCache, scheduler);
            } catch (RuntimeException | Error failure) {
                dispatchCache.close();
                throw failure;
            }
        }
    }

    /** @return running process-wide scheduler shared by all AE2 grids */
    public static DispatchProposalScheduler scheduler() {
        return runningResources().scheduler();
    }

    /** @return cache reserved for dispatch capacity and proposal calculations */
    public static TrinityComputationCache dispatchComputationCache() {
        return runningResources().dispatchCache();
    }

    /**
     * Cancels proposal work and cached dispatch calculations owned by one unloaded Grid.
     *
     * @param gridScope unloaded Grid publication scope
     */
    public static void clearGrid(long gridScope) {
        DispatchResources current = runningResources();
        try {
            current.scheduler().clearGrid(gridScope);
        } finally {
            current.dispatchCache().clearGrid(gridScope);
        }
    }

    /** Cancels outstanding proposals and stops the independent executor. */
    public static void stop() {
        synchronized (LIFECYCLE_LOCK) {
            DispatchResources closing = resources;
            if (closing == null) {
                return;
            }
            resources = null;
            try {
                closing.scheduler().close();
            } finally {
                closing.dispatchCache().close();
            }
        }
    }

    private static DispatchResources runningResources() {
        DispatchResources current = resources;
        if (current == null) {
            throw new IllegalStateException("The Trinity dispatch proposal scheduler is not running");
        }
        return current;
    }

    private record DispatchResources(
                                     TrinityComputationCache dispatchCache,
                                     DispatchProposalScheduler scheduler) {}
}
