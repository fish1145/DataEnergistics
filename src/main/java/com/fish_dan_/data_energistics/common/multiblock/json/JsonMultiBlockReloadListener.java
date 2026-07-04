package com.fish_dan_.data_energistics.common.multiblock.json;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;
import java.util.Objects;

/**
 * Server datapack reload listener that atomically applies JSON multiblock definitions.
 */
public final class JsonMultiBlockReloadListener
                                                extends SimplePreparableReloadListener<Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition>> {

    private final JsonMultiBlockDefinitionRegistry registry;
    private final JsonMultiBlockDefinitionLoader loader;

    public JsonMultiBlockReloadListener(JsonMultiBlockDefinitionRegistry registry) {
        this(registry, new MdlibJsonMultiBlockDefinitionLoader());
    }

    public JsonMultiBlockReloadListener(JsonMultiBlockDefinitionRegistry registry, JsonMultiBlockDefinitionLoader loader) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    @Override
    protected Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> prepare(ResourceManager resourceManager,
                                                                                ProfilerFiller profiler) {
        return this.loader.load(resourceManager);
    }

    @Override
    protected void apply(Map<JsonMultiBlockStructureKey, JsonMultiBlockDefinition> definitions,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        this.registry.applyJsonDefinitions(definitions.values());
    }
}
