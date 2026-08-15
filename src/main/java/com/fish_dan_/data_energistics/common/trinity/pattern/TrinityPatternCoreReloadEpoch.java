package com.fish_dan_.data_energistics.common.trinity.pattern;

import com.fish_dan_.data_energistics.common.recipe.RecipeReloadEpoch;

/**
 * Compatibility view of the shared recipe reload epoch used by retained Trinity pattern state.
 */
public final class TrinityPatternCoreReloadEpoch {

    private TrinityPatternCoreReloadEpoch() {}

    /**
     * Advances the epoch after tags and recipes have been rebound.
     *
     * @return new epoch
     */
    public static long advance() {
        return RecipeReloadEpoch.advance();
    }

    /**
     * @return current reload epoch observed by pattern core block entities
     */
    public static long current() {
        return RecipeReloadEpoch.current();
    }
}
