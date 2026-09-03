package com.fish_dan_.data_energistics.common.trinity.core;

/**
 * Defines the fixed physical capacities of the three Trinity pattern processing core tiers.
 *
 * <p>
 * The current capacities are complete nine-slot UI rows. Paired 3.1.3 capacities support direct upgrades without
 * renumbering existing physical slots.
 * </p>
 */
public enum TrinityPatternCoreTier {

    STANDARD(64, 72),
    EXTENDED(128, 144),
    OVERLIMIT(512, 576);

    private final int powerOfTwoPatternCapacity;
    private final int patternCapacity;

    TrinityPatternCoreTier(int powerOfTwoPatternCapacity, int patternCapacity) {
        this.powerOfTwoPatternCapacity = powerOfTwoPatternCapacity;
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

    /** Returns whether a 3.1.3 persisted capacity may upgrade into the current block capacity. */
    public static boolean matchesPowerOfTwoCapacity(int persistedCapacity, int currentCapacity) {
        for (TrinityPatternCoreTier tier : values()) {
            if (tier.powerOfTwoPatternCapacity == persistedCapacity && tier.patternCapacity == currentCapacity) {
                return true;
            }
        }
        return false;
    }
}
