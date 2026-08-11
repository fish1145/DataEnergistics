package com.fish_dan_.data_energistics.configuration.snapshot;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

/**
 * Immutable planning settings published as one configuration snapshot.
 */
public record TrinityCraftingSettings(
                                      int maxSccKeys,
                                      int maxBindingVariants,
                                      int maxScheduleStates,
                                      int graphRebuildBudgetMs,
                                      int plannerThreads,
                                      int plannerQueueCapacity,
                                      int dynamicRetryMaxTicks,
                                      CraftingQuantityMode defaultQuantityMode)
        implements DataEnergisticsSettings.TrinityCrafting {

    public TrinityCraftingSettings {
        if (maxSccKeys <= 0 || maxBindingVariants <= 0 || maxScheduleStates <= 0 || graphRebuildBudgetMs <= 0 ||
                plannerThreads <= 0 || plannerThreads > 8 || plannerQueueCapacity <= 0 || dynamicRetryMaxTicks <= 0) {
            throw new IllegalArgumentException("Trinity crafting budgets must be positive and use at most 8 workers");
        }
    }

    public static TrinityCraftingSettings defaults(int availableProcessors) {
        return new TrinityCraftingSettings(
                64,
                32768,
                500000,
                4,
                DataEnergisticsConfiguration.TrinityCraftingSchema.recommendedPlannerThreads(availableProcessors),
                128,
                200,
                CraftingQuantityMode.NET_NEW);
    }

    public static TrinityCraftingSettings defaults() {
        return defaults(Runtime.getRuntime().availableProcessors());
    }
}
