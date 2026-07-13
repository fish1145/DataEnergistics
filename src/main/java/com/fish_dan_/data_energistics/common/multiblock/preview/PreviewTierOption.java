package com.fish_dan_.data_energistics.common.multiblock.preview;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * One stable business tier choice used to resolve a multiblock predicate candidate.
 *
 * @param value   positive value exchanged by selection models
 * @param label   player-facing tier label
 * @param blockId block selected for this tier
 */
public record PreviewTierOption(int value, Component label, ResourceLocation blockId) {

    /**
     * Validates and detaches the mutable component supplied by a preview definition.
     */
    public PreviewTierOption {
        if (value < 1) {
            throw new IllegalArgumentException("Preview tier values must be positive: " + value);
        }
        if (label == null) {
            throw new IllegalArgumentException("Preview tier option requires a label");
        }
        if (blockId == null) {
            throw new IllegalArgumentException("Preview tier option requires a block id");
        }
        label = label.copy();
    }

    /**
     * Returns a detached label so callers cannot mutate definition-owned text.
     */
    @Override
    public Component label() {
        return this.label.copy();
    }
}
