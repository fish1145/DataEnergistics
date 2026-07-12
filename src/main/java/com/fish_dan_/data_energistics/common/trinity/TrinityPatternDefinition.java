package com.fish_dan_.data_energistics.common.trinity;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * Immutable slot-local definition retained once and referenced by queued crafting groups.
 *
 * <p>
 * A missing resolution is allowed only for migrated V1 work whose recipe identity could not be reconstructed.
 * Such work remains refundable and cannot execute until a later cache refresh reconstructs its identity.
 * </p>
 */
public final class TrinityPatternDefinition {

    private final long id;
    private final ItemStack pattern;
    @Nullable
    private final TrinityPatternRecipeIdResolvers.Resolution resolution;

    /**
     * Creates one stable slot-local definition.
     *
     * @param id         non-negative slot-local identity
     * @param pattern    complete encoded pattern stack with count one
     * @param resolution captured recipe identity, or {@code null} for unresolved migrated work
     */
    private TrinityPatternDefinition(long id, ItemStack pattern,
                                     @Nullable TrinityPatternRecipeIdResolvers.Resolution resolution) {
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
     * Creates a definition whose stable recipe identity was resolved at insertion or V2 parsing time.
     *
     * @param id         non-negative slot-local identity
     * @param pattern    complete encoded pattern stack with count one
     * @param resolution captured resolver and recipe identity
     * @return resolved definition
     */
    public static TrinityPatternDefinition resolved(long id, ItemStack pattern,
                                                    TrinityPatternRecipeIdResolvers.Resolution resolution) {
        if (resolution == null) {
            throw new IllegalArgumentException("A resolved Trinity pattern definition requires a recipe identity");
        }
        return new TrinityPatternDefinition(id, pattern, resolution);
    }

    /**
     * Creates an unresolved definition retained only while migrating a no-version V1 queue.
     *
     * @param id      non-negative slot-local identity
     * @param pattern complete encoded pattern stack with count one
     * @return initially non-executable but refundable V1 definition
     */
    public static TrinityPatternDefinition unresolvedV1(long id, ItemStack pattern) {
        return new TrinityPatternDefinition(id, pattern, null);
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
     * @return captured recipe resolution, or {@code null} for unresolved migrated work
     */
    @Nullable
    public TrinityPatternRecipeIdResolvers.Resolution resolution() {
        return this.resolution;
    }

    /**
     * @return whether this definition has a stable recipe identity and may become executable
     */
    public boolean resolved() {
        return this.resolution != null;
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
