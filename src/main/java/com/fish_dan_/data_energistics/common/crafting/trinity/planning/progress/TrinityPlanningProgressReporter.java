package com.fish_dan_.data_energistics.common.crafting.trinity.planning.progress;

/**
 * Asynchronous planning progress boundary.
 *
 * <p>
 * Implementations must not retain a menu, player, grid, level, CPU, or world. A reporter is closed before its menu
 * revision is cancelled, so late worker publications become harmless no-ops.
 * </p>
 */
public interface TrinityPlanningProgressReporter {

    /** @return a stateless reporter for planning paths that have no confirmation-menu consumer. */
    static TrinityPlanningProgressReporter none() {
        return NoProgressReporter.INSTANCE;
    }

    /** Accepts one immutable latest-state snapshot when this request is still open. */
    void publish(TrinityPlanningProgressSnapshot snapshot);

    /** Stateless no-op avoids nullable progress branches in shared planning utilities. */
    enum NoProgressReporter implements TrinityPlanningProgressReporter {

        INSTANCE;

        @Override
        public void publish(TrinityPlanningProgressSnapshot snapshot) {}
    }
}
