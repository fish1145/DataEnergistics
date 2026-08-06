package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server;

/**
 * Owns the server-level round-robin boundary that interleaves physical calls from independent AE Grids.
 */
public interface TrinityServerDispatchScheduler {

    /**
     * Creates an empty scheduler with no retained server or Grid ownership.
     *
     * @return independent server dispatch scheduler
     */
    static TrinityServerDispatchScheduler create() {
        return new TrinityServerDispatchSchedulerImpl();
    }

    /**
     * Opens registration for one server tick and rejects an unfinished previous tick.
     */
    void beginTick();

    /**
     * Registers one prepared Grid participant for the current tick.
     *
     * @param participant prepared Grid dispatch boundary
     */
    void register(CraftingDispatchParticipant participant);

    /**
     * Runs every registered Grid in physical-call round-robin order and completes their metrics.
     */
    void dispatchTick();

    /**
     * Clears an unfinished tick during logical-server shutdown.
     */
    void reset();
}
