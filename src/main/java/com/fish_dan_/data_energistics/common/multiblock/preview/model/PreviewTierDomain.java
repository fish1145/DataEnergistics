package com.fish_dan_.data_energistics.common.multiblock.preview.model;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ordered choices for one independently selectable business tier category.
 *
 * @param id           stable non-blank category id
 * @param label        player-facing category label
 * @param options      ordered non-empty tier choices
 * @param defaultValue option value selected for a new preview session
 */
public record PreviewTierDomain(String id,
                                Component label,
                                List<PreviewTierOption> options,
                                int defaultValue) {

    /**
     * Copies ordered choices and rejects ambiguous values or block ids.
     */
    public PreviewTierDomain {
        if (id.isBlank()) {
            throw new IllegalArgumentException("Preview tier domain id cannot be blank");
        }
        if (options.isEmpty()) {
            throw new IllegalArgumentException("Preview tier domain requires at least one option: " + id);
        }
        label = label.copy();
        options = List.copyOf(options);
        Set<Integer> values = new HashSet<>();
        Set<ResourceLocation> blockIds = new HashSet<>();
        for (PreviewTierOption option : options) {
            if (!values.add(option.value())) {
                throw new IllegalArgumentException(
                        "Duplicate preview tier value " + option.value() + " in domain " + id);
            }
            if (!blockIds.add(option.blockId())) {
                throw new IllegalArgumentException(
                        "Duplicate preview tier block " + option.blockId() + " in domain " + id);
            }
        }
        boolean defaultFound = options.stream().anyMatch(option -> option.value() == defaultValue);
        if (!defaultFound) {
            throw new IllegalArgumentException(
                    "Preview tier domain " + id + " does not contain default value " + defaultValue);
        }
    }

    /**
     * Returns a detached label so callers cannot mutate definition-owned text.
     */
    @Override
    public Component label() {
        return this.label.copy();
    }

    /**
     * Resolves a declared option by its stable value.
     *
     * @param value selected tier value
     * @return matching declared option
     */
    public PreviewTierOption option(int value) {
        for (PreviewTierOption option : this.options) {
            if (option.value() == value) {
                return option;
            }
        }
        throw new IllegalArgumentException("Unknown preview tier value " + value + " for domain " + this.id);
    }

    /**
     * Tests whether a block belongs to this tier category.
     *
     * @param blockId candidate block id
     * @return true when one declared option selects the block
     */
    public boolean containsBlock(ResourceLocation blockId) {
        return this.options.stream().anyMatch(option -> option.blockId().equals(blockId));
    }
}
