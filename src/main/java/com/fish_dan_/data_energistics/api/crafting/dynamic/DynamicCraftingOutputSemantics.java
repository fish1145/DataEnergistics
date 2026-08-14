package com.fish_dan_.data_energistics.api.crafting.dynamic;

import java.util.List;
import java.util.Objects;

/**
 * Immutable dynamic-output declarations for one logical invocation of the supplied outer pattern details.
 *
 * @param outputs non-empty dynamic physical outputs in deterministic declaration order
 */
public record DynamicCraftingOutputSemantics(List<DynamicCraftingOutput> outputs) {

    /**
     * Copies and validates declarations before an adapter can expose them to crafting execution.
     */
    public DynamicCraftingOutputSemantics {
        Objects.requireNonNull(outputs, "Dynamic crafting output semantics must not be null");
        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("Dynamic crafting output semantics require at least one output");
        }
        outputs = List.copyOf(outputs);
    }
}
