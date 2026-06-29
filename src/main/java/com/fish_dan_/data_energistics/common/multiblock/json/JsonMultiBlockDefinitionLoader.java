package com.fish_dan_.data_energistics.common.multiblock.json;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.Map;

/**
 * Loader for JSON multiblock resources.
 *
 * <p>
 * The loader owns path-to-key conversion and MDLib JSON parsing. Callers receive a keyed map so reload application can
 * replace the registry atomically.
 */
public interface JsonMultiBlockDefinitionLoader {

    /**
     * Loads all JSON resources below {@code data/<namespace>/multiblock/} from a server resource manager.
     */
    Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> load(ResourceManager resourceManager);

    /**
     * Loads already-read JSON documents keyed by their path relative to {@code multiblock/}, without the {@code .json}
     * suffix.
     */
    Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> load(Map<ResourceLocation, String> resources);

    /**
     * Parses one resource document into a definition.
     */
    JsonMultiBlockDefinition parse(ResourceLocation resourceId, Reader reader);
}
