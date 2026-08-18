package com.fish_dan_.data_energistics.integration.viewer.xei.transfer;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Collects localized recipe-category names exposed by the active recipe viewers.
 */
public final class PatternProviderRecipeTypeNames {

    private static final Map<ResourceLocation, Source> SOURCES = new LinkedHashMap<>();

    private PatternProviderRecipeTypeNames() {}

    /**
     * Installs or replaces one viewer-backed name source for the current client runtime.
     */
    public static synchronized void register(ResourceLocation sourceId, Source source) {
        SOURCES.put(sourceId, source);
    }

    /**
     * Removes a viewer-backed source when its runtime becomes unavailable.
     */
    public static synchronized void unregister(ResourceLocation sourceId) {
        SOURCES.remove(sourceId);
    }

    /**
     * Resolves every distinct localized title currently exposed for one recipe type.
     */
    public static List<String> resolve(ResourceLocation recipeTypeId) {
        List<Source> sources;
        synchronized (PatternProviderRecipeTypeNames.class) {
            sources = List.copyOf(SOURCES.values());
        }

        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Source source : sources) {
            for (Component name : source.resolve(recipeTypeId)) {
                String localizedName = name.getString();
                if (!localizedName.isBlank()) {
                    names.add(localizedName);
                }
            }
        }
        return List.copyOf(names);
    }

    /**
     * Resolves the first localized viewer title, falling back to the stable recipe type ID.
     */
    public static Component resolveDisplayName(ResourceLocation recipeTypeId) {
        List<String> names = resolve(recipeTypeId);
        return Component.literal(names.isEmpty() ? recipeTypeId.toString() : names.getFirst());
    }

    /**
     * Resolves localized category components from one active recipe viewer.
     */
    @FunctionalInterface
    public interface Source {

        /**
         * Returns the localized category names that this viewer exposes for the requested recipe type.
         */
        List<Component> resolve(ResourceLocation recipeTypeId);
    }
}
