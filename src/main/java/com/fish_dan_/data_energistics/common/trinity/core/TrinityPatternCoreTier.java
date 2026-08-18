package com.fish_dan_.data_energistics.common.trinity.core;

/**
 * Defines the fixed physical capacities of the three Trinity pattern processing core tiers.
 *
 * <p>
 * The current capacities are complete nine-slot UI rows. Their paired legacy capacities are accepted only while
 * loading V3 persistent state, allowing the core inventory to grow without renumbering existing physical slots.
 * </p>
 */
public enum TrinityPatternCoreTier {

    STANDARD(64, 72),
    EXTENDED(128, 144),
    OVERLIMIT(512, 576);

    private final int legacyPatternCapacity;
    private final int patternCapacity;

    TrinityPatternCoreTier(int legacyPatternCapacity, int patternCapacity) {
        this.legacyPatternCapacity = legacyPatternCapacity;
        this.patternCapacity = patternCapacity;
    }

    /** Returns the current capacity, always divisible by nine. */
    public int patternCapacity() {
        return this.patternCapacity;
    }

    /** Returns whether the supplied capacity belongs to one current physical core tier. */
    public static boolean supportsPatternCapacity(int capacity) {
        for (TrinityPatternCoreTier tier : values()) {
            if (tier.patternCapacity == capacity) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether a V3 persisted capacity may migrate into the supplied current block capacity.
     */
    public static boolean matchesLegacyCapacity(int persistedCapacity, int currentCapacity) {
        for (TrinityPatternCoreTier tier : values()) {
            if (tier.legacyPatternCapacity == persistedCapacity && tier.patternCapacity == currentCapacity) {
                return true;
            }
        }
        return false;
    }
}
