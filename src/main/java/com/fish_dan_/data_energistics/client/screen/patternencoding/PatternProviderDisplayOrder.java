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
 * Builds a client-only filtered view while preserving the server provider order.
 */
final class PatternProviderDisplayOrder {

    private PatternProviderDisplayOrder() {}

    /**
     * Retains only search matches without recalculating or changing their server-defined order.
     */
    static ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> order(
                                                                              ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> providers,
                                                                              String query,
                                                                              PatternProviderSearchContext searchContext,
                                                                              Function<ResourceLocation, String> defaultNameResolver,
                                                                              Function<ResourceLocation, ObjectList<String>> recipeTypeNameResolver) {
        String normalizedQuery = PinyinUtil.normalizeSearch(query);
        if (normalizedQuery.isEmpty()) {
            return ObjectLists.unmodifiable(new ObjectArrayList<>(providers));
        }

        ObjectList<PatternEncodingPreviewMenu.SyncedPatternProvider> filtered = new ObjectArrayList<>();
        Object2ObjectOpenHashMap<ResourceLocation, String> defaultNames = new Object2ObjectOpenHashMap<>();
        Object2ObjectOpenHashMap<ResourceLocation, ObjectList<String>> recipeTypeNames = new Object2ObjectOpenHashMap<>();

        for (PatternEncodingPreviewMenu.SyncedPatternProvider provider : providers) {
            ObjectList<String> searchTerms = buildSearchTerms(provider, defaultNameResolver, recipeTypeNameResolver,
                    defaultNames, recipeTypeNames);
            searchContext.addTerms(provider, searchTerms);
            if (PatternProviderSearchMatcher.matches(searchTerms, normalizedQuery)) {
                filtered.add(provider);
            }
        }
        return ObjectLists.unmodifiable(filtered);
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
