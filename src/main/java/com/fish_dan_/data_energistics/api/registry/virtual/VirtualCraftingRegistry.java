package com.fish_dan_.data_energistics.api.registry.virtual;

import com.fish_dan_.data_energistics.api.crafting.dispatch.VirtualCraftingOutputAdapter;

import org.jetbrains.annotations.NotNull;

/**
 * Registration facet for stateless virtual crafting output adapters.
 */
public interface VirtualCraftingRegistry {

    /**
     * Registers one adapter that may claim a declared pattern output.
     *
     * @param adapter stateless output adapter
     */
    void registerOutputAdapter(@NotNull VirtualCraftingOutputAdapter adapter);
}
