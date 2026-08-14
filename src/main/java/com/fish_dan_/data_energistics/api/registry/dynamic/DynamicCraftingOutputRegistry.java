package com.fish_dan_.data_energistics.api.registry.dynamic;

import com.fish_dan_.data_energistics.api.crafting.dynamic.DynamicCraftingOutputAdapter;

/**
 * Registration-stage surface for stateless dynamic crafting-output adapters.
 */
public interface DynamicCraftingOutputRegistry {

    /**
     * Registers one adapter under the stable ID returned by {@link DynamicCraftingOutputAdapter#id()}.
     *
     * @param adapter stateless pattern-output adapter
     */
    void register(DynamicCraftingOutputAdapter adapter);
}
