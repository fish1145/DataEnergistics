package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration.TrinityCraftingSchema;

import org.jspecify.annotations.Nullable;

/**
 * Owns the bounded planning gateway and its isolated execution lanes for one logical server lifetime.
 *
 * <p>
 * A process-wide lifecycle prevents every AE2 grid from multiplying the configured worker and queue budgets.
 * </p>
 */
public final class TrinityPlanningGatewayLifecycle {

    private static @Nullable TrinityPlanningGateway gateway;

    private TrinityPlanningGatewayLifecycle() {}

    /**
     * Starts the shared gateway after COMMON configuration has loaded.
     *
     * @param settings immutable planning budgets for this server lifetime
     */
    public static synchronized void start(TrinityCraftingSchema settings) {
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
     * @return the running server-lifetime cache shared across every Trinity computation lane
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
