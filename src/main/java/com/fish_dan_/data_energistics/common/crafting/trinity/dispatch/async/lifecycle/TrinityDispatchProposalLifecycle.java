package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.lifecycle;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningGatewayLifecycle;

/**
 * Owns the independent bounded dispatch-proposal executor for one logical server lifetime.
 */
public final class TrinityDispatchProposalLifecycle {

    private static final Object LIFECYCLE_LOCK = new Object();
    private static volatile DispatchProposalScheduler scheduler;

    private TrinityDispatchProposalLifecycle() {}

    /** Starts the dispatch executor after COMMON configuration has loaded. */
    public static void start() {
        synchronized (LIFECYCLE_LOCK) {
            if (scheduler != null) {
                throw new IllegalStateException("The Trinity dispatch proposal scheduler is already running");
            }
            scheduler = DispatchProposalScheduler.create(TrinityPlanningGatewayLifecycle::computationCache);
        }
    }

    /** @return running process-wide scheduler shared by all AE2 grids */
    public static DispatchProposalScheduler scheduler() {
        DispatchProposalScheduler current = scheduler;
        if (current == null) {
            throw new IllegalStateException("The Trinity dispatch proposal scheduler is not running");
        }
        return current;
    }

    /** Cancels outstanding proposals and stops the independent executor. */
    public static void stop() {
        synchronized (LIFECYCLE_LOCK) {
            if (scheduler == null) {
                return;
            }
            DispatchProposalScheduler closing = scheduler;
            scheduler = null;
            closing.close();
        }
    }
}
