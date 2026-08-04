package com.fish_dan_.data_energistics.config;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class TrinityCraftingConfigTest {

    @Test
    void exposesDocumentedLargeModpackDefaults() {
        TrinityCraftingConfig.Settings settings = TrinityCraftingConfig.Settings.defaults(12);

        assertEquals(64, settings.maxSccKeys());
        assertEquals(32_768, settings.maxBindingVariants());
        assertEquals(500_000, settings.maxScheduleStates());
        assertEquals(4, settings.graphRebuildBudgetMs());
        assertEquals(6, settings.plannerThreads());
        assertEquals(128, settings.plannerQueueCapacity());
        assertEquals(200, settings.dynamicRetryMaxTicks());
        assertEquals(CraftingQuantityMode.NET_NEW, settings.defaultQuantityMode());
    }

    @Test
    void migratesOnlyTheLegacyBindingVariantDefault() {
        assertEquals(32_768, TrinityCraftingConfig.migrateBindingVariantLimit(512));
        assertEquals(4_096, TrinityCraftingConfig.migrateBindingVariantLimit(4_096));
    }

    @Test
    void boundsDefaultWorkerCountToHalfOfHostProcessorsAndEight() {
        assertEquals(1, TrinityCraftingConfig.recommendedPlannerThreads(1));
        assertEquals(1, TrinityCraftingConfig.recommendedPlannerThreads(2));
        assertEquals(7, TrinityCraftingConfig.recommendedPlannerThreads(15));
        assertEquals(8, TrinityCraftingConfig.recommendedPlannerThreads(16));
        assertEquals(8, TrinityCraftingConfig.recommendedPlannerThreads(128));
        assertThrows(IllegalArgumentException.class, () -> TrinityCraftingConfig.recommendedPlannerThreads(0));
    }

    @Test
    void rejectsDisabledOrOvercommittedProgrammaticBudgets() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityCraftingConfig.Settings(
                        0,
                        512,
                        500_000,
                        4,
                        6,
                        128,
                        200,
                        CraftingQuantityMode.NET_NEW));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityCraftingConfig.Settings(
                        64,
                        512,
                        500_000,
                        4,
                        9,
                        128,
                        200,
                        CraftingQuantityMode.NET_NEW));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TrinityCraftingConfig.Settings(
                        64,
                        512,
                        500_000,
                        4,
                        6,
                        128,
                        200,
                        null));
    }
}
