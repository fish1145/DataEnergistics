package com.fish_dan_.data_energistics.client.screen.patternencoding;

import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingPreviewMenu;
import com.fish_dan_.data_energistics.util.PinyinUtil;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;

/**
 * Builds the client-only provider view without changing the synchronized provider order.
 */
final class PatternProviderDisplayOrder {

    private PatternProviderDisplayOrder() {}

    /**
     * Produces three stable groups: current recipe-type supporters, search matches, then every remaining provider.
     */
    static List<PatternEncodingPreviewMenu.SyncedPatternProvider> order(
                                                                        List<PatternEncodingPreviewMenu.SyncedPatternProvider> providers,
                                                                        @Nullable ResourceLocation currentRecipeTypeId,
                                                                        String query,
                                                                        Function<ResourceLocation, String> defaultNameResolver,
                                                                        Function<ResourceLocation, List<String>> recipeTypeNameResolver,
                                                                        BiPredicate<String, String> searchMatcher) {
        String normalizedQuery = PinyinUtil.normalizeSearch(query);
        List<PatternEncodingPreviewMenu.SyncedPatternProvider> currentType = new ArrayList<>();
        List<PatternEncodingPreviewMenu.SyncedPatternProvider> searchMatches = new ArrayList<>();
        List<PatternEncodingPreviewMenu.SyncedPatternProvider> remaining = new ArrayList<>();
        Map<ResourceLocation, String> defaultNames = new HashMap<>();
        Map<ResourceLocation, List<String>> recipeTypeNames = new HashMap<>();

        for (PatternEncodingPreviewMenu.SyncedPatternProvider provider : providers) {
            if (currentRecipeTypeId != null && provider.supportedRecipeTypeIds().contains(currentRecipeTypeId)) {
                currentType.add(provider);
            } else if (!normalizedQuery.isEmpty() && searchMatcher.test(
                    buildSearchSource(provider, defaultNameResolver, recipeTypeNameResolver,
                            defaultNames, recipeTypeNames),
                    normalizedQuery)) {
                        searchMatches.add(provider);
                    } else {
                        remaining.add(provider);
                    }
        }

        List<PatternEncodingPreviewMenu.SyncedPatternProvider> ordered = new ArrayList<>(providers.size());
        ordered.addAll(currentType);
        ordered.addAll(searchMatches);
        ordered.addAll(remaining);
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
