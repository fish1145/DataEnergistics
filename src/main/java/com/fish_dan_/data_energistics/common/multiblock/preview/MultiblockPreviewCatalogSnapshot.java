package com.fish_dan_.data_energistics.common.multiblock.preview;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable ordered preview catalog built from one definition registry revision.
 *
 * @param definitionRevision source registry generation
 * @param specs              controller specs in stable registration order
 */
public record MultiblockPreviewCatalogSnapshot(
                                               long definitionRevision,
                                               Map<ResourceLocation, MultiblockPreviewSpec> specs) {

    /**
     * Copies and validates the entire catalog generation before publication to XEI or host UIs.
     */
    public MultiblockPreviewCatalogSnapshot {
        if (definitionRevision < 0L || specs == null) {
            throw new IllegalArgumentException("Invalid multiblock preview catalog snapshot");
        }
        LinkedHashMap<ResourceLocation, MultiblockPreviewSpec> copy = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, MultiblockPreviewSpec> entry : specs.entrySet()) {
            ResourceLocation controllerId = entry.getKey();
            MultiblockPreviewSpec spec = entry.getValue();
            if (controllerId == null || spec == null || !controllerId.equals(spec.controllerId())) {
                throw new IllegalArgumentException("Multiblock preview catalog entry does not match its controller id");
            }
            if (spec.definitionRevision() != definitionRevision) {
                throw new IllegalArgumentException("Multiblock preview catalog mixes definition revisions");
            }
            copy.put(controllerId, spec);
        }
        specs = Collections.unmodifiableMap(copy);
    }

    /**
     * Resolves a required controller spec.
     *
     * @param controllerId stable controller id
     * @return matching revision-bound spec
     */
    public MultiblockPreviewSpec require(ResourceLocation controllerId) {
        MultiblockPreviewSpec spec = this.specs.get(controllerId);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown multiblock preview controller: " + controllerId);
        }
        return spec;
    }
}
