package com.fish_dan_.data_energistics.common.trinity;

import java.util.Map;

/**
 * Immutable user-selected behavior shared by all Trinity automatic-structure build descriptors.
 *
 * @param buildRequested whether the action should execute a build instead of only retaining UI selection state
 * @param repeatCount    count requested for each adjustable repeatable structure unit
 * @param tierSelections one-based tier index selected for each predicate category
 */
public record TrinityAutoBuildOptions(boolean buildRequested,
                                      int repeatCount,
                                      Map<String, Integer> tierSelections) {

    /** Lowest repeat count accepted by the current CPU and crafting child structure definitions. */
    public static final int MIN_REPEAT_COUNT = 1;
    /** Highest repeat count accepted by the current CPU and crafting child structure definitions. */
    public static final int MAX_REPEAT_COUNT = 12;

    /**
     * Copies and validates all mutable client-provided selection data before it reaches auto-build planning.
     */
    public TrinityAutoBuildOptions {
        if (repeatCount < MIN_REPEAT_COUNT || repeatCount > MAX_REPEAT_COUNT) {
            throw new IllegalArgumentException("Trinity auto-build repeat count must be between " + MIN_REPEAT_COUNT +
                    " and " + MAX_REPEAT_COUNT + ": " + repeatCount);
        }
        tierSelections = Map.copyOf(tierSelections);
        TrinityAutoBuildBlockMap.validateTierSelections(tierSelections);
    }
}
