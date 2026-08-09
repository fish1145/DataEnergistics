package com.fish_dan_.data_energistics.common.trinity.pattern;

import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdResolution;

import net.minecraft.world.item.ItemStack;

/**
 * Immutable slot-local definition retained once and referenced by queued crafting groups.
 */
public final class TrinityPatternDefinition {

    private final long id;
    private final ItemStack pattern;
    private final TrinityPatternRecipeIdResolution resolution;

    /**
     * Creates one stable slot-local definition.
     *
     * @param id         non-negative slot-local identity
     * @param pattern    complete encoded pattern stack with count one
     * @param resolution captured resolver and recipe identity
     */
    private TrinityPatternDefinition(long id, ItemStack pattern,
                                     TrinityPatternRecipeIdResolution resolution) {
        if (id < 0L) {
            throw new IllegalArgumentException("Trinity pattern definition ID must not be negative: " + id);
        }
        if (pattern.isEmpty() || pattern.getCount() != 1) {
            throw new IllegalArgumentException("Trinity pattern definition requires one encoded pattern item");
        }
        this.id = id;
        this.pattern = pattern.copy();
        this.resolution = resolution;
    }

    /**
     * Creates a definition whose stable recipe identity was resolved at insertion or persistence parsing time.
     *
     * @param id         non-negative slot-local identity
     * @param pattern    complete encoded pattern stack with count one
     * @param resolution captured resolver and recipe identity
     * @return resolved definition
     */
    public static TrinityPatternDefinition resolved(long id, ItemStack pattern,
                                                    TrinityPatternRecipeIdResolution resolution) {
        if (resolution == null) {
            throw new IllegalArgumentException("A resolved Trinity pattern definition requires a recipe identity");
        }
        return new TrinityPatternDefinition(id, pattern, resolution);
    }

    /**
     * @return stable slot-local ID referenced by queued groups
     */
    public long id() {
        return this.id;
    }

    /**
     * @return defensive copy of the complete encoded pattern
     */
    public ItemStack pattern() {
        return this.pattern.copy();
    }

    /**
     * @return captured resolver and recipe identity
     */
    public TrinityPatternRecipeIdResolution resolution() {
        return this.resolution;
    }

    /**
     * Compares a complete encoded pattern without exposing the retained mutable stack.
     *
     * @param candidate normalized pattern candidate
     * @return whether item, components, and count match
     */
    public boolean matchesPattern(ItemStack candidate) {
        return ItemStack.matches(this.pattern, candidate);
    }
}
