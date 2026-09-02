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
 * Prioritizes providers associated with the encoded recipe, preserving server order within each group.
 */
final class PatternProviderDisplayOrder {

    private PatternProviderDisplayOrder() {}

    /**
     * Applies text filtering and a stable recipe-match partition using the open panel's lazy XEI context.
     */
    static ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> order(
                                                                              PatternEncodingPreviewMenu.SyncedPatternProviderList providerState,
                                                                              String query,
                                                                              PatternProviderSearchContext searchContext,
                                                                              Function<ResourceLocation, String> defaultNameResolver,
                                                                              Function<ResourceLocation, ObjectList<String>> recipeTypeNameResolver) {
        String normalizedQuery = PinyinUtil.normalizeSearch(query);
        if (normalizedQuery.isEmpty() && !searchContext.hasRecipeReference()) {
            return providerState.providers();
        }

        ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> matching = new ObjectArrayList<>();
        ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> remaining = new ObjectArrayList<>();
        Object2ObjectOpenHashMap<ResourceLocation, String> defaultNames = new Object2ObjectOpenHashMap<>();
        Object2ObjectOpenHashMap<ResourceLocation, ObjectList<String>> recipeTypeNames = new Object2ObjectOpenHashMap<>();

        for (PatternEncodingPreviewMenu.SyncedPatternProvider provider : providerState.providers()) {
            ObjectList<String> searchTerms = normalizedQuery.isEmpty() ? null :
                    buildSearchTerms(provider, defaultNameResolver, recipeTypeNameResolver, defaultNames, recipeTypeNames);
            boolean recipeMatch = searchContext.matchRecipe(provider, providerState.rankingContext(), searchTerms);
            if (searchTerms != null && !PatternProviderSearchMatcher.matches(searchTerms, normalizedQuery)) {
                continue;
            }
            (recipeMatch ? matching : remaining).add(provider);
        }
        matching.addAll(remaining);
        return ObjectLists.unmodifiable(matching);
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
            terms.add(recipeTypeId.toString());
            terms.addAll(recipeTypeNames.computeIfAbsent(recipeTypeId, recipeTypeNameResolver));
        }
        return terms;
    }
}
