package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.client.util.PinyinUtil;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                                                                        Function<ResourceLocation, List<String>> recipeTypeNameResolver) {
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
            List<String> searchTerms = buildSearchTerms(provider, defaultNameResolver, recipeTypeNameResolver,
                    defaultNames, recipeTypeNames);
            boolean searchMatches = matchesSearchTerms(searchTerms, normalizedQuery);
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

    private static boolean matchesSearchTerms(List<String> terms, String normalizedQuery) {
        StringBuilder combined = new StringBuilder();
        for (String term : terms) {
            combined.append(PinyinUtil.normalizeSearch(term));
        }
        String normalizedSource = combined.toString();
        if (normalizedSource.contains(normalizedQuery) || isSubsequenceMatch(normalizedQuery, normalizedSource)) {
            return true;
        }
        for (String term : terms) {
            if (PinyinUtil.matchesNormalizedJech(term, normalizedQuery)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSubsequenceMatch(String query, String source) {
        int queryIndex = 0;
        for (int sourceIndex = 0; sourceIndex < source.length() && queryIndex < query.length(); sourceIndex++) {
            if (source.charAt(sourceIndex) == query.charAt(queryIndex)) {
                queryIndex++;
            }
        }
        return queryIndex == query.length();
    }

    private static List<String> buildSearchTerms(
                                                 PatternEncodingPreviewMenu.SyncedPatternProvider provider,
                                                 Function<ResourceLocation, String> defaultNameResolver,
                                                 Function<ResourceLocation, List<String>> recipeTypeNameResolver,
                                                 Map<ResourceLocation, String> defaultNames,
                                                 Map<ResourceLocation, List<String>> recipeTypeNames) {
        List<String> terms = new ArrayList<>();
        terms.add(provider.displayName().getString());
        terms.add(defaultNames.computeIfAbsent(provider.iconItemId(), defaultNameResolver));
        terms.add(provider.iconItemId().toString());
        for (ResourceLocation recipeTypeId : provider.supportedRecipeTypeIds()) {
            terms.addAll(recipeTypeNames.computeIfAbsent(recipeTypeId, recipeTypeNameResolver));
        }
        return terms;
    }
}
