package com.fish_dan_.data_energistics.common.multiblock.json.loading;

import com.fish_dan_.data_energistics.common.multiblock.json.registry.JsonMultiBlockDefinitionRegistry;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * NeoForge event handler that attaches the JSON multiblock reload listener to server datapack reloads.
 */
public final class JsonMultiBlockReloadEventHandler {

    private final JsonMultiBlockDefinitionRegistry registry;

    public JsonMultiBlockReloadEventHandler(JsonMultiBlockDefinitionRegistry registry) {
        this.registry = registry;
    }

    @SubscribeEvent
    public void addReloadListener(AddReloadListenerEvent event) {
        event.addListener(new JsonMultiBlockReloadListener(this.registry));
    }
}
