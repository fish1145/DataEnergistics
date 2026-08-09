package com.fish_dan_.data_energistics.common.multiblock.preview;

import com.fish_dan_.data_energistics.common.multiblock.json.registry.JsonMultiBlockDefinitionRegistrySnapshot;

import net.minecraft.resources.ResourceLocation;

/**
 * Adapts one controller's business metadata to the common preview model at a definition generation.
 */
public interface MultiblockPreviewSpecFactory {

    /**
     * Returns the stable controller id owned by this factory.
     */
    ResourceLocation controllerId();

    /**
     * Builds one spec using definitions from exactly one atomically published registry generation.
     *
     * @param definitions active registry generation
     * @return revision-bound preview spec
     */
    MultiblockPreviewSpec create(JsonMultiBlockDefinitionRegistrySnapshot definitions);
}
