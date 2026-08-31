package com.fish_dan_.data_energistics.common.crafting.trinity.planning.request;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration.TrinityCraftingSchema;

/**
 * Immutable per-request limits captured before Trinity planning leaves the server thread.
 *
 * @param maxSccKeys         maximum keys accepted in one strongly connected component
 * @param maxBindingVariants maximum deduplicated variants materialized for one request
 * @param maxScheduleStates  local state limit for each independent route or component search
 * @param planningBudgetMs   initial bounded planning allowance in milliseconds
 */
public record TrinityPlanningLimits(
                                    int maxSccKeys,
                                    int maxBindingVariants,
                                    int maxScheduleStates,
                                    int planningBudgetMs) {

    /**
     * Rejects invalid limits before they can enter cache keys or background work.
     */
    public TrinityPlanningLimits {
        if (maxSccKeys <= 0 || maxBindingVariants <= 0 || maxScheduleStates <= 0 || planningBudgetMs <= 0) {
            throw new IllegalArgumentException("Trinity planning limits must be positive");
        }
    }

    /**
     * Captures the algorithm-relevant values from a live configuration instance exactly once.
     *
     * @param settings mutable server-thread configuration
     * @return immutable limits for one planning request
     */
    public static TrinityPlanningLimits capture(TrinityCraftingSchema settings) {
        if (settings == null) {
            throw new IllegalArgumentException("Trinity planning limits require a configuration source");
        }
        return new TrinityPlanningLimits(
                settings.maxSccKeys,
                settings.maxBindingVariants,
                settings.maxScheduleStates,
                settings.planningBudgetMs);
    }

    /**
     * Reconstructs a detached compatibility value for callers of the former configuration-based API.
     * Mutating the returned object cannot alter this request.
     *
     * @return independent configuration value containing these planning limits
     */
    public TrinityCraftingSchema detachedSchema() {
        TrinityCraftingSchema settings = new TrinityCraftingSchema();
        settings.maxSccKeys = this.maxSccKeys;
        settings.maxBindingVariants = this.maxBindingVariants;
        settings.maxScheduleStates = this.maxScheduleStates;
        settings.planningBudgetMs = this.planningBudgetMs;
        return settings;
    }
}
