package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.util.PinyinUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatternProviderDisplayOrderTest {

    private static final ResourceLocation CURRENT_RECIPE_TYPE = id("compressing");
    private static final ResourceLocation MIXING_RECIPE_TYPE = id("mixing");

    @Test
    void emptyAndUnmatchedQueriesKeepEveryProviderInStableRecipeTypeGroups() {
        var providers = List.of(
                provider(1, "Other A", List.of()),
                provider(2, "Current A", List.of(CURRENT_RECIPE_TYPE)),
                provider(3, "Other B", List.of(MIXING_RECIPE_TYPE)),
                provider(4, "Current B", List.of(CURRENT_RECIPE_TYPE)));

        assertEquals(List.of(2L, 4L, 1L, 3L), orderIds(providers, ""));
        assertEquals(List.of(2L, 4L, 1L, 3L), orderIds(providers, "no provider matches this"));
    }

    @Test
    void searchMatchesCustomDefaultAndLocalizedRecipeTypeNamesWithoutOverridingCurrentType() {
        var providers = List.of(
                provider(1, "Player Renamed Provider", List.of()),
                provider(2, "Generic Provider", List.of()),
                provider(3, "Recipe Provider", List.of(MIXING_RECIPE_TYPE)),
                provider(4, "Double Match", List.of(CURRENT_RECIPE_TYPE)),
                provider(5, "Current Only", List.of(CURRENT_RECIPE_TYPE)),
                provider(6, "Other", List.of()));
        Map<ResourceLocation, String> defaultNames = Map.of(icon(2), "Default Machine Name");

        assertEquals(List.of(4L, 5L, 1L, 2L, 3L, 6L),
                orderIds(providers, "PLAYER RENAMED", defaultNames, Map.of()));
        assertEquals(List.of(4L, 5L, 2L, 1L, 3L, 6L),
                orderIds(providers, "default machine", defaultNames, Map.of()));
        assertEquals(List.of(4L, 5L, 3L, 1L, 2L, 6L),
                orderIds(providers, "本地化混合", defaultNames,
                        Map.of(MIXING_RECIPE_TYPE, List.of("本地化混合"))));
        assertEquals(List.of(4L, 5L, 1L, 2L, 3L, 6L),
                orderIds(providers, "double match", defaultNames, Map.of()));
    }

    @Test
    void updatedProviderDataAndLanguageNamesAreReadForEachOrderingPass() {
        var initialProviders = List.of(
                provider(1, "Other A", List.of()),
                provider(3, "Other B", List.of()),
                provider(2, "Current", List.of(CURRENT_RECIPE_TYPE)));
        var updatedProviders = List.of(
                provider(1, "Other A", List.of()),
                provider(3, "Fresh Provider", List.of()),
                provider(2, "Current", List.of(CURRENT_RECIPE_TYPE)));

        assertEquals(List.of(2L, 1L, 3L), orderIds(initialProviders, "fresh"));
        assertEquals(List.of(2L, 3L, 1L), orderIds(updatedProviders, "fresh"));

        var localizedProviders = List.of(
                provider(6, "Other", List.of(MIXING_RECIPE_TYPE)),
                provider(7, "Localized", List.of(CURRENT_RECIPE_TYPE)));
        assertEquals(List.of(6L, 7L),
                orderIds(localizedProviders, null, "compressing", Map.of(),
                        Map.of(CURRENT_RECIPE_TYPE, List.of("压缩"))));
        assertEquals(List.of(7L, 6L),
                orderIds(localizedProviders, null, "compressing", Map.of(),
                        Map.of(CURRENT_RECIPE_TYPE, List.of("Compressing"))));
    }

    private static List<Long> orderIds(
                                       List<PatternEncodingPreviewMenu.SyncedPatternProvider> providers,
                                       String query) {
        return orderIds(providers, query, Map.of(), Map.of());
    }

    private static List<Long> orderIds(
                                       List<PatternEncodingPreviewMenu.SyncedPatternProvider> providers,
                                       String query,
                                       Map<ResourceLocation, String> defaultNames,
                                       Map<ResourceLocation, List<String>> recipeTypeNames) {
        return orderIds(providers, CURRENT_RECIPE_TYPE, query, defaultNames, recipeTypeNames);
    }

    private static List<Long> orderIds(
                                       List<PatternEncodingPreviewMenu.SyncedPatternProvider> providers,
                                       @Nullable ResourceLocation currentRecipeTypeId,
                                       String query,
                                       Map<ResourceLocation, String> defaultNames,
                                       Map<ResourceLocation, List<String>> recipeTypeNames) {
        return PatternProviderDisplayOrder.order(
                providers,
                currentRecipeTypeId,
                query,
                id -> defaultNames.getOrDefault(id, ""),
                id -> recipeTypeNames.getOrDefault(id, List.of()),
                (source, filter) -> PinyinUtil.normalizeSearch(source).contains(PinyinUtil.normalizeSearch(filter)))
                .stream()
                .map(PatternEncodingPreviewMenu.SyncedPatternProvider::id)
                .toList();
    }

    private static PatternEncodingPreviewMenu.SyncedPatternProvider provider(
                                                                             long id,
                                                                             String displayName,
                                                                             List<ResourceLocation> supportedRecipeTypeIds) {
        return new PatternEncodingPreviewMenu.SyncedPatternProvider(
                id,
                Component.literal(displayName),
                icon(id),
                true,
                true,
                9,
                0,
                List.of(),
                supportedRecipeTypeIds,
                null);
    }

    private static ResourceLocation icon(long id) {
        return id("icon_" + id);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
