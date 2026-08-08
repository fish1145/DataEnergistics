package com.fish_dan_.data_energistics.api.registry.recipe;

import org.jetbrains.annotations.NotNull;

/**
 * Common-setup declaration surface for Trinity pattern recipe-ID resolvers.
 */
public interface TrinityPatternRecipeIdRegistry {

    /**
     * Stages one resolver in the current plugin transaction.
     *
     * @param resolver uniquely identified resolver
     */
    void register(@NotNull TrinityPatternRecipeIdResolver resolver);
}
