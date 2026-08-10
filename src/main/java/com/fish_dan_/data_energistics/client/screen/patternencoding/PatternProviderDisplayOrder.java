package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.util.PinyinUtil;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Builds the client-only search view while preserving the server ranking within each display bucket.
 */
final class PatternProviderDisplayOrder {

    private PatternProviderDisplayOrder() {}

    /**
     * Moves search matches to the front of their server-defined match group without recalculating provider rank.
     */
    static List<PatternEncodingPreviewMenu.SyncedPatternProvider> order(
                                                                        List<PatternEncodingPreviewMenu.SyncedPatternProvider> providers,
                                                                        String query,
                                                                        Function<ResourceLocation, String> defaultNameResolver,
                                                                        Function<ResourceLocation, List<String>> recipeTypeNameResolver,
                                                                        BiPredicate<String, String> searchMatcher) {
        String normalizedQuery = PinyinUtil.normalizeSearch(query);
        if (normalizedQuery.isEmpty()) {
            return List.copyOf(providers);
        }

        List<PatternEncodingPreviewMenu.SyncedPatternProvider> exactSearchMatches = new ArrayList<>();
        List<PatternEncodingPreviewMenu.SyncedPatternProvider> exactRemaining = new ArrayList<>();
        List<PatternEncodingPreviewMenu.SyncedPatternProvider> otherSearchMatches = new ArrayList<>();
        List<PatternEncodingPreviewMenu.SyncedPatternProvider> otherRemaining = new ArrayList<>();
        Map<ResourceLocation, String> defaultNames = new HashMap<>();
        Map<ResourceLocation, List<String>> recipeTypeNames = new HashMap<>();

        for (PatternEncodingPreviewMenu.SyncedPatternProvider provider : providers) {
            boolean searchMatches = searchMatcher.test(
                    buildSearchSource(provider, defaultNameResolver, recipeTypeNameResolver,
                            defaultNames, recipeTypeNames),
                    normalizedQuery);
            if (provider.exactViewerMatch()) {
                (searchMatches ? exactSearchMatches : exactRemaining).add(provider);
            } else {
                (searchMatches ? otherSearchMatches : otherRemaining).add(provider);
            }
        }

        List<PatternEncodingPreviewMenu.SyncedPatternProvider> ordered = new ArrayList<>(providers.size());
        ordered.addAll(exactSearchMatches);
        ordered.addAll(exactRemaining);
        ordered.addAll(otherSearchMatches);
        ordered.addAll(otherRemaining);
        return List.copyOf(ordered);
    }

    private static String buildSearchSource(
                                            PatternEncodingPreviewMenu.SyncedPatternProvider provider,
                                            Function<ResourceLocation, String> defaultNameResolver,
                                            Function<ResourceLocation, List<String>> recipeTypeNameResolver,
                                            Map<ResourceLocation, String> defaultNames,
                                            Map<ResourceLocation, List<String>> recipeTypeNames) {
        StringBuilder source = new StringBuilder(provider.displayName().getString());
        source.append(' ')
                .append(defaultNames.computeIfAbsent(provider.iconItemId(), defaultNameResolver))
                .append(' ')
                .append(provider.iconItemId());
        for (ResourceLocation recipeTypeId : provider.supportedRecipeTypeIds()) {
            for (String localizedName : recipeTypeNames.computeIfAbsent(recipeTypeId, recipeTypeNameResolver)) {
                source.append(' ').append(localizedName);
            }
        }
        return source.toString();
    }
}
