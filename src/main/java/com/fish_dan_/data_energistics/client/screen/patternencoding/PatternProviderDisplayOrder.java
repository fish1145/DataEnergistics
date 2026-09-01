package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.client.util.PinyinUtil;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.function.Function;

/**
 * Builds the client-only search view while preserving the server ranking within each display bucket.
 */
final class PatternProviderDisplayOrder {

    private PatternProviderDisplayOrder() {}

    /**
     * Moves search matches to the front of their server-defined match group without recalculating provider rank.
     */
    static ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> order(
                                                                              ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> providers,
                                                                              String query,
                                                                              Function<ResourceLocation, String> defaultNameResolver,
                                                                              Function<ResourceLocation, ObjectList<String>> recipeTypeNameResolver) {
        String normalizedQuery = PinyinUtil.normalizeSearch(query);
        if (normalizedQuery.isEmpty()) {
            return ObjectLists.unmodifiable(new ObjectArrayList<>(providers));
        }

        ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> exactSearchMatches = new ObjectArrayList<>();
        ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> exactRemaining = new ObjectArrayList<>();
        ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> otherSearchMatches = new ObjectArrayList<>();
        ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> otherRemaining = new ObjectArrayList<>();
        Object2ObjectOpenHashMap<ResourceLocation, String> defaultNames = new Object2ObjectOpenHashMap<>();
        Object2ObjectOpenHashMap<ResourceLocation, ObjectList<String>> recipeTypeNames = new Object2ObjectOpenHashMap<>();

        for (PatternEncodingPreviewMenu.SyncedPatternProvider provider : providers) {
            ObjectList<String> searchTerms = buildSearchTerms(provider, defaultNameResolver, recipeTypeNameResolver,
                    defaultNames, recipeTypeNames);
            boolean searchMatches = matchesSearchTerms(searchTerms, normalizedQuery);
            if (provider.exactViewerMatch()) {
                (searchMatches ? exactSearchMatches : exactRemaining).add(provider);
            } else {
                (searchMatches ? otherSearchMatches : otherRemaining).add(provider);
            }
        }

        ObjectArrayList<PatternEncodingPreviewMenu.SyncedPatternProvider> ordered = new ObjectArrayList<>(providers.size());
        ordered.addAll(exactSearchMatches);
        ordered.addAll(exactRemaining);
        ordered.addAll(otherSearchMatches);
        ordered.addAll(otherRemaining);
        return ObjectLists.unmodifiable(ordered);
    }

    private static boolean matchesSearchTerms(ObjectList<String> terms, String normalizedQuery) {
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

    private static ObjectList<String> buildSearchTerms(
                                                       PatternEncodingPreviewMenu.SyncedPatternProvider provider,
                                                       Function<ResourceLocation, String> defaultNameResolver,
                                                       Function<ResourceLocation, ObjectList<String>> recipeTypeNameResolver,
                                                       Object2ObjectOpenHashMap<ResourceLocation, String> defaultNames,
                                                       Object2ObjectOpenHashMap<ResourceLocation, ObjectList<String>> recipeTypeNames) {
        ObjectList<String> terms = new ObjectArrayList<>();
        terms.add(provider.displayName().getString());
        terms.add(defaultNames.computeIfAbsent(provider.iconItemId(), defaultNameResolver));
        terms.add(provider.iconItemId().toString());
        for (ResourceLocation recipeTypeId : provider.supportedRecipeTypeIds()) {
            terms.addAll(recipeTypeNames.computeIfAbsent(recipeTypeId, recipeTypeNameResolver));
        }
        return terms;
    }
}
