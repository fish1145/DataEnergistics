package com.fish_dan_.data_energistics.client.transfer;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Collects workstation item IDs exposed by the active recipe viewers for one recipe type.
 */
public final class PatternProviderViewerWorkstations {

    private static final Map<ResourceLocation, Source> SOURCES = new LinkedHashMap<>();

    private PatternProviderViewerWorkstations() {}

    /**
     * Installs or replaces one viewer-backed workstation source for the current client runtime.
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
     * Resolves the canonical workstation item IDs advertised by the viewer that initiated the transfer.
     */
    public static List<ResourceLocation> resolve(ResourceLocation sourceId,
                                                 ResourceLocation recipeTypeId) {
        Source source;
        synchronized (PatternProviderViewerWorkstations.class) {
            source = SOURCES.get(sourceId);
        }
        if (source == null) {
            throw new IllegalStateException("No pattern viewer workstation source is registered for " + sourceId);
        }
        return new LinkedHashSet<>(source.resolve(recipeTypeId)).stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
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
