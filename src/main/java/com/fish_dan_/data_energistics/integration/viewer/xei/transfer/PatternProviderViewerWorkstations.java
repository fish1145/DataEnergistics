package com.fish_dan_.data_energistics.integration.viewer.xei.transfer;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.util.Comparator;
import java.util.List;

/**
 * Collects workstation item IDs exposed by the active recipe viewers for one recipe type.
 */
public final class PatternProviderViewerWorkstations {

    private static final Object2ObjectLinkedOpenHashMap<ResourceLocation, Source> SOURCES = new Object2ObjectLinkedOpenHashMap<>();
    private static long revision;

    private PatternProviderViewerWorkstations() {}

    /**
     * Installs or replaces one viewer-backed workstation source for the current client runtime.
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
     * Resolves the canonical workstation item IDs advertised by every active viewer for one recipe type.
     */
    public static ObjectList<ResourceLocation> resolve(ResourceLocation recipeTypeId) {
        ObjectList<Source> sources;
        synchronized (PatternProviderViewerWorkstations.class) {
            sources = new ObjectArrayList<>(SOURCES.values());
        }
        ObjectLinkedOpenHashSet<ResourceLocation> resolved = new ObjectLinkedOpenHashSet<>();
        for (Source source : sources) {
            try {
                resolved.addAll(source.resolve(recipeTypeId));
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to resolve pattern-viewer workstations for recipe type {}",
                        recipeTypeId,
                        exception);
            }
        }
        ObjectArrayList<ResourceLocation> ordered = new ObjectArrayList<>(resolved);
        ordered.sort(Comparator.comparing(ResourceLocation::toString));
        return ObjectLists.unmodifiable(ordered);
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
     * Resolves workstation item IDs from one active recipe viewer.
     */
    @FunctionalInterface
    public interface Source {

        /**
         * Returns every workstation item ID that this viewer exposes for the requested recipe type.
         */
        List<ResourceLocation> resolve(ResourceLocation recipeTypeId);
    }
}
