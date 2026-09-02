package com.fish_dan_.data_energistics.integration.viewer.xei.transfer;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.List;

/**
 * Collects localized recipe-category names exposed by the active recipe viewers.
 */
public final class PatternProviderRecipeTypeNames {

    private static final Object2ObjectLinkedOpenHashMap<ResourceLocation, Source> SOURCES = new Object2ObjectLinkedOpenHashMap<>();
    private static long revision;

    private PatternProviderRecipeTypeNames() {}

    /**
     * Installs or replaces one viewer-backed name source for the current client runtime.
     */
    public static synchronized void register(ResourceLocation sourceId, Source source) {
        SOURCES.put(sourceId, source);
        incrementRevision();
    }

    /**
     * Removes a viewer-backed source when its runtime becomes unavailable.
     */
    public static synchronized void unregister(ResourceLocation sourceId) {
        if (SOURCES.containsKey(sourceId)) {
            SOURCES.remove(sourceId);
            incrementRevision();
        }
    }

    /**
     * Resolves every distinct localized title currently exposed for one recipe type.
     */
    public static ObjectList<String> resolve(ResourceLocation recipeTypeId) {
        ObjectList<Source> sources;
        synchronized (PatternProviderRecipeTypeNames.class) {
            sources = new ObjectArrayList<>(SOURCES.values());
        }

        ObjectLinkedOpenHashSet<String> names = new ObjectLinkedOpenHashSet<>();
        for (Source source : sources) {
            try {
                for (Component name : source.resolve(recipeTypeId)) {
                    String localizedName = name.getString();
                    if (!localizedName.isBlank()) {
                        names.add(localizedName);
                    }
                }
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to resolve pattern-viewer name for recipe type {}",
                        recipeTypeId,
                        exception);
            }
        }
        return ObjectLists.unmodifiable(new ObjectArrayList<>(names));
    }

    /**
     * Resolves the first localized viewer title, falling back to the stable recipe type ID.
     */
    public static Component resolveDisplayName(ResourceLocation recipeTypeId) {
        ObjectList<String> names = resolve(recipeTypeId);
        return Component.literal(names.isEmpty() ? recipeTypeId.toString() : names.getFirst());
    }

    public static synchronized long revision() {
        return revision;
    }

    private static void incrementRevision() {
        if (revision != Long.MAX_VALUE) {
            revision++;
        }
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
