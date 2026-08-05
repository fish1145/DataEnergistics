package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings.TrinityCrafting;

/**
 * Owns the single bounded planning pool for one logical server lifetime.
 *
 * <p>
 * A process-wide lifecycle prevents every AE2 grid from multiplying the configured worker and queue budgets.
 * </p>
 */
public final class TrinityPlanningGatewayLifecycle {

    private static TrinityPlanningGateway gateway;

    private TrinityPlanningGatewayLifecycle() {}

    /**
     * Starts the shared gateway after COMMON configuration has loaded.
     *
     * @param settings immutable planning budgets for this server lifetime
     */
    public static synchronized void start(TrinityCrafting settings) {
        if (gateway != null) {
            throw new IllegalStateException("The Trinity planning gateway is already running");
        }
        gateway = TrinityPlanningGateway.create(settings);
    }

    /**
     * @return running shared gateway used by every grid
     */
    public static synchronized TrinityPlanningGateway gateway() {
        if (gateway == null) {
            throw new IllegalStateException("The Trinity planning gateway is not running");
        }
        return gateway;
    }

    /**
     * @return server-lifetime cache shared by planning and pure dispatch computations
     */
    public static synchronized TrinityComputationCache computationCache() {
        return gateway().computationCache();
    }

    /**
     * Cooperatively cancels workers and queued planning jobs after the server has stopped.
     */
    public static synchronized void stop() {
        if (gateway == null) {
            return;
        }
        TrinityPlanningGateway closing = gateway;
        gateway = null;
        closing.close();
    }
}
