package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.lifecycle;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule.DispatchProposalScheduler;

/**
 * Owns the independent bounded dispatch-proposal executor for one logical server lifetime.
 */
public final class TrinityDispatchProposalLifecycle {

    private static DispatchProposalScheduler scheduler;

    private TrinityDispatchProposalLifecycle() {}

    /** Starts the dispatch executor after COMMON configuration has loaded. */
    public static synchronized void start() {
        if (scheduler != null) {
            throw new IllegalStateException("The Trinity dispatch proposal scheduler is already running");
        }
        scheduler = DispatchProposalScheduler.create();
    }

    /** @return running process-wide scheduler shared by all AE2 grids */
    public static synchronized DispatchProposalScheduler scheduler() {
        if (scheduler == null) {
            throw new IllegalStateException("The Trinity dispatch proposal scheduler is not running");
        }
        return scheduler;
    }

    /** Cancels outstanding proposals and stops the independent executor. */
    public static synchronized void stop() {
        if (scheduler == null) {
            return;
        }
        DispatchProposalScheduler closing = scheduler;
        scheduler = null;
        closing.close();
    }
}
