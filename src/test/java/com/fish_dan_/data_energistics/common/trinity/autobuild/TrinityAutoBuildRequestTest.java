package com.fish_dan_.data_energistics.common.trinity.autobuild;

import com.fish_dan_.data_energistics.common.trinity.core.TrinityCoreKind;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityAutoBuildRequestTest {

    @Test
    void storageCategoryMapsEveryTierToItsExplicitCoreId() {
        assertTier(TrinityAutoBuildBlockMap.STORAGE_CORE, 1, "me_digital_storage_core_1m");
        assertTier(TrinityAutoBuildBlockMap.STORAGE_CORE, 2, "me_digital_storage_core_4m");
        assertTier(TrinityAutoBuildBlockMap.STORAGE_CORE, 3, "me_digital_storage_core_16m");
        assertTier(TrinityAutoBuildBlockMap.STORAGE_CORE, 4, "me_digital_storage_core_64m");
        assertTier(TrinityAutoBuildBlockMap.STORAGE_CORE, 5, "me_digital_storage_core_256m");
        assertTier(TrinityAutoBuildBlockMap.STORAGE_CORE, 6, "me_digital_storage_core_1g");
        assertTier(TrinityAutoBuildBlockMap.STORAGE_CORE, 7, "me_digital_storage_core_4g");
        assertTier(TrinityAutoBuildBlockMap.STORAGE_CORE, 8, "me_digital_storage_core_16g");
        assertTier(TrinityAutoBuildBlockMap.STORAGE_CORE, 9, "me_digital_storage_core_64g");
        assertTier(TrinityAutoBuildBlockMap.STORAGE_CORE, 10, "me_digital_storage_core_256g");
        assertEquals(TrinityCoreKind.STORAGE_TYPES,
                TrinityAutoBuildBlockMap.coreKind(TrinityAutoBuildBlockMap.STORAGE_CORE));
    }

    @Test
    void parallelCpuCategoryMapsEveryTierToItsExplicitCoreId() {
        assertTier(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 1, "me_digital_merged_storage_core_1m");
        assertTier(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 2, "me_digital_merged_storage_core_4m");
        assertTier(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 3, "me_digital_merged_storage_core_16m");
        assertTier(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 4, "me_digital_merged_storage_core_64m");
        assertTier(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 5, "me_digital_merged_storage_core_256m");
        assertTier(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 6, "me_digital_merged_storage_core_1g");
        assertTier(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 7, "me_digital_merged_storage_core_4g");
        assertTier(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 8, "me_digital_merged_storage_core_16g");
        assertTier(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 9, "me_digital_merged_storage_core_64g");
        assertTier(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 10, "me_digital_merged_storage_core_256g");
        assertEquals(TrinityCoreKind.PARALLEL_CPU,
                TrinityAutoBuildBlockMap.coreKind(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE));
    }

    @Test
    void patternCategoryMapsEveryTierToItsExplicitCoreId() {
        assertTier(TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE, 1, "me_digital_pattern_processing_core");
        assertTier(TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE, 2,
                "extended_me_digital_pattern_processing_core");
        assertTier(TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE, 3,
                "overlimit_me_digital_pattern_processing_core");
        assertEquals(TrinityCoreKind.PATTERN_PROCESSING,
                TrinityAutoBuildBlockMap.coreKind(TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE));
    }

    @Test
    void optionsCopyAndExposeAnImmutableTierSelectionMap() {
        Map<String, Integer> selections = new HashMap<>();
        selections.put(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 1);
        TrinityAutoBuildOptions options = new TrinityAutoBuildOptions(true, 1, selections);

        selections.put(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 2);

        assertTrue(options.buildRequested());
        assertEquals(1, options.tierSelections().get(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE));
        assertThrows(UnsupportedOperationException.class,
                () -> options.tierSelections().put(TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE, 1));
    }

    @Test
    void optionsAcceptRepeatBoundsAndRejectOutOfRangeCounts() {
        TrinityAutoBuildOptions minimum = new TrinityAutoBuildOptions(false, 1, Map.of());
        TrinityAutoBuildOptions maximum = new TrinityAutoBuildOptions(true, 12, Map.of());

        assertFalse(minimum.buildRequested());
        assertEquals(12, maximum.repeatCount());
        assertThrows(IllegalArgumentException.class, () -> new TrinityAutoBuildOptions(true, 0, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new TrinityAutoBuildOptions(true, 13, Map.of()));
    }

    @Test
    void optionsRejectUnknownCategoriesAndInvalidOneBasedTierIndexes() {
        assertThrows(IllegalArgumentException.class,
                () -> new TrinityAutoBuildOptions(true, 1, Map.of("unknown", 1)));
        assertThrows(IllegalArgumentException.class, () -> new TrinityAutoBuildOptions(true, 1,
                Map.of(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 0)));
        assertThrows(IllegalArgumentException.class, () -> new TrinityAutoBuildOptions(true, 1,
                Map.of(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 11)));
        assertThrows(IllegalArgumentException.class, () -> new TrinityAutoBuildOptions(true, 1,
                Map.of(TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE, 0)));
        assertThrows(IllegalArgumentException.class, () -> new TrinityAutoBuildOptions(true, 1,
                Map.of(TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE, 4)));
    }

    @Test
    void selectedTierBlockValidationRequiresTheMatchingStructureCategoryAndRepeatRange() {
        assertThrows(IllegalArgumentException.class, () -> TrinityAutoBuildBlockMap.selectedTierBlocks(
                TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX,
                2,
                Map.of(TrinityAutoBuildBlockMap.STORAGE_CORE, 1)));
        assertThrows(IllegalArgumentException.class, () -> TrinityAutoBuildBlockMap.selectedTierBlocks(
                TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX,
                1,
                Map.of(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 1)));
        assertThrows(IllegalArgumentException.class, () -> TrinityAutoBuildBlockMap.selectedTierBlocks(
                TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX,
                1,
                Map.of(
                        TrinityAutoBuildBlockMap.STORAGE_CORE, 1,
                        TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE, 1)));
        assertThrows(IllegalArgumentException.class, () -> TrinityAutoBuildBlockMap.selectedTierBlocks(
                TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX,
                TrinityAutoBuildOptions.MAX_REPEAT_COUNT + 1,
                Map.of(TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE, 1)));
    }

    @Test
    void requestPreservesStructureSelectorIndexesAndRejectsIndexesOutsideTheUiRange() {
        TrinityAutoBuildOptions options = new TrinityAutoBuildOptions(false, 1, Map.of());
        TrinityAutoBuildRequest main = new TrinityAutoBuildRequest(TrinityAutoBuildRequest.MAIN_STRUCTURE_INDEX, options);
        TrinityAutoBuildRequest crafting = new TrinityAutoBuildRequest(
                TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX,
                options);

        assertEquals(0, main.structureIndex());
        assertEquals(2, crafting.structureIndex());
        assertEquals(1, TrinityAutoBuildRequest.CPU_STRUCTURE_INDEX);
        assertEquals(2, TrinityAutoBuildRequest.CRAFTING_STRUCTURE_INDEX);
        assertThrows(IllegalArgumentException.class, () -> new TrinityAutoBuildRequest(-1, options));
        assertThrows(IllegalArgumentException.class, () -> new TrinityAutoBuildRequest(3, options));
    }

    @Test
    void categoryMetadataIsCopiedBeforeExposure() {
        Map<String, List<ResourceLocation>> categories = TrinityAutoBuildBlockMap.categories();

        assertEquals(List.of(
                TrinityAutoBuildBlockMap.STORAGE_CORE,
                TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE,
                TrinityAutoBuildBlockMap.PATTERN_PROCESSING_CORE),
                List.copyOf(categories.keySet()));
        assertThrows(UnsupportedOperationException.class, () -> categories.put("unknown", List.of()));
        assertThrows(UnsupportedOperationException.class,
                () -> categories.get(TrinityAutoBuildBlockMap.PARALLEL_CPU_CORE).add(ResourceLocation.parse("minecraft:air")));
    }

    private static void assertTier(String category, int tierIndex, String expectedPath) {
        assertEquals(ResourceLocation.fromNamespaceAndPath("data_energistics", expectedPath),
                TrinityAutoBuildBlockMap.blockId(category, tierIndex));
    }
}
