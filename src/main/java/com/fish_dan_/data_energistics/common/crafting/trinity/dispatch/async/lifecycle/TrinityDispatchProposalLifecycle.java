package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.lifecycle;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningGatewayLifecycle;

import org.jspecify.annotations.Nullable;

/**
 * Owns the proposal executor while borrowing the global Trinity computation cache for one logical server lifetime.
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
            DispatchProposalScheduler scheduler = DispatchProposalScheduler.create(
                    TrinityPlanningGatewayLifecycle::computationCache);
            resources = new DispatchResources(scheduler);
        }
    }

    /** @return running process-wide scheduler shared by all AE2 grids */
    public static DispatchProposalScheduler scheduler() {
        return runningResources().scheduler();
    }

    /** @return global cache shared by planning, replanning, and dispatch calculations */
    public static TrinityComputationCache dispatchComputationCache() {
        runningResources();
        return TrinityPlanningGatewayLifecycle.computationCache();
    }

    /**
     * Cancels proposal work owned by one unloaded Grid. The planning lifecycle clears the shared cache once after this
     * scheduler boundary has stopped publishing.
     *
     * @param gridScope unloaded Grid publication scope
     */
    public static void clearGrid(long gridScope) {
        runningResources().scheduler().clearGrid(gridScope);
    }

    /** Cancels outstanding proposals and stops the independent executor. */
    public static void stop() {
        synchronized (LIFECYCLE_LOCK) {
            DispatchResources closing = resources;
            if (closing == null) {
                return;
            }
            resources = null;
            closing.scheduler().close();
        }
    }

    private static DispatchResources runningResources() {
        DispatchResources current = resources;
        if (current == null) {
            throw new IllegalStateException("The Trinity dispatch proposal scheduler is not running");
        }
        return current;
    }

    private record DispatchResources(DispatchProposalScheduler scheduler) {}
}
