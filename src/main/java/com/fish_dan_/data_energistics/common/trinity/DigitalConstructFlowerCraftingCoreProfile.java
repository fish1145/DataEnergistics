package com.fish_dan_.data_energistics.common.trinity;

import java.util.Objects;

/**
 * Pattern recognition capability resolved from trinity pattern processing cores in the crafting child structure.
 */
public record DigitalConstructFlowerCraftingCoreProfile(int patternCoreCount, int patternCapacity) {

    public static final DigitalConstructFlowerCraftingCoreProfile EMPTY = new DigitalConstructFlowerCraftingCoreProfile(0, 0);

    public DigitalConstructFlowerCraftingCoreProfile {
        if (patternCoreCount < 0) {
            throw new IllegalArgumentException("Crafting core profile pattern core count must not be negative");
        }
        if (patternCapacity < 0) {
            throw new IllegalArgumentException("Crafting core profile pattern capacity must not be negative");
        }
        if (patternCoreCount == 0 && patternCapacity != 0) {
            throw new IllegalArgumentException("Crafting core profile with pattern capacity must expose at least one core");
        }
        if (patternCoreCount > 0 && patternCapacity == 0) {
            throw new IllegalArgumentException("Crafting core profile with pattern cores must expose positive capacity");
        }
    }

    /**
     * Creates a builder for pattern processing cores found in one crafting child structure.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns whether this profile enables pattern recognition.
     */
    public boolean active() {
        return this.patternCapacity > 0;
    }

    /**
     * Builder that accumulates pattern processing core metadata found while scanning a crafting child structure.
     */
    public static final class Builder {

        private int patternCoreCount;
        private int patternCapacity;

        private Builder() {}

        /**
         * Adds one pattern processing core contribution to this profile.
         */
        public void add(TrinityCoreComponent component) {
            Objects.requireNonNull(component, "component");
            if (component.kind() != TrinityCoreKind.PATTERN_PROCESSING) {
                return;
            }
            this.patternCoreCount = Math.addExact(this.patternCoreCount, 1);
            this.patternCapacity = Math.addExact(this.patternCapacity, component.patternCapacity());
        }

        /**
         * Builds the immutable crafting core profile.
         */
        public DigitalConstructFlowerCraftingCoreProfile build() {
            if (this.patternCoreCount == 0) {
                return EMPTY;
            }
            return new DigitalConstructFlowerCraftingCoreProfile(this.patternCoreCount, this.patternCapacity);
        }
    }
}
