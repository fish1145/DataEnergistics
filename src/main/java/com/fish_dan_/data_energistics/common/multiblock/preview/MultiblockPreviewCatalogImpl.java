package com.fish_dan_.data_energistics.common.multiblock.preview;

import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinitionRegistry;
import com.fish_dan_.data_energistics.common.multiblock.json.JsonMultiBlockDefinitionRegistrySnapshot;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Default ordered catalog that invokes registered controller factories against one atomic definition snapshot.
 */
public final class MultiblockPreviewCatalogImpl implements MultiblockPreviewCatalog {

    private final JsonMultiBlockDefinitionRegistry definitionRegistry;
    private final List<MultiblockPreviewSpecFactory> factories;

    /**
     * Captures a stable unique factory order while retaining the live definition registry reload boundary.
     *
     * @param definitionRegistry active JSON definition registry
     * @param factories          ordered controller factories
     */
    public MultiblockPreviewCatalogImpl(JsonMultiBlockDefinitionRegistry definitionRegistry,
                                        List<MultiblockPreviewSpecFactory> factories) {
        if (definitionRegistry == null || factories == null) {
            throw new IllegalArgumentException("Multiblock preview catalog arguments cannot be null");
        }
        List<MultiblockPreviewSpecFactory> copy = new ArrayList<>(factories);
        Set<ResourceLocation> controllerIds = new HashSet<>();
        for (MultiblockPreviewSpecFactory factory : copy) {
            if (factory == null || factory.controllerId() == null) {
                throw new IllegalArgumentException("Multiblock preview factories cannot contain null entries or ids");
            }
            if (!controllerIds.add(factory.controllerId())) {
                throw new IllegalArgumentException("Duplicate multiblock preview controller: " +
                        factory.controllerId());
            }
        }
        this.definitionRegistry = definitionRegistry;
        this.factories = List.copyOf(copy);
    }

    @Override
    public MultiblockPreviewCatalogSnapshot snapshot() {
        JsonMultiBlockDefinitionRegistrySnapshot definitions = this.definitionRegistry.snapshot();
        Map<ResourceLocation, MultiblockPreviewSpec> specs = new LinkedHashMap<>();
        for (MultiblockPreviewSpecFactory factory : this.factories) {
            MultiblockPreviewSpec spec = factory.create(definitions);
            if (!factory.controllerId().equals(spec.controllerId())) {
                throw new IllegalStateException("Multiblock preview factory returned a spec for another controller: " +
                        factory.controllerId() + " != " + spec.controllerId());
            }
            if (spec.definitionRevision() != definitions.revision()) {
                throw new IllegalStateException("Multiblock preview factory returned a stale definition revision: " +
                        factory.controllerId());
            }
            specs.put(spec.controllerId(), spec);
        }
        return new MultiblockPreviewCatalogSnapshot(definitions.revision(), specs);
    }
}
