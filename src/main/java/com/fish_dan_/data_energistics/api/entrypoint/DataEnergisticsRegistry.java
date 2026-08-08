package com.fish_dan_.data_energistics.api.entrypoint;

import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdRegistry;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalRegistry;
import com.fish_dan_.data_energistics.api.registry.virtual.VirtualCraftingRegistry;

import org.jetbrains.annotations.NotNull;

/**
 * Root registration-stage surface passed to a Data Energistics plugin.
 *
 * <p>
 * All facets refer to the same staging transaction. A plugin can therefore register terminals, provider
 * integrations and virtual output adapters from one entrypoint without coordinating multiple annotations.
 * </p>
 */
public interface DataEnergisticsRegistry {

    /**
     * @return universal-terminal declaration facet
     */
    @NotNull
    UniversalTerminalRegistry universalTerminals();

    /**
     * @return pattern-provider lifecycle declaration facet
     */
    @NotNull
    PatternProviderRegistry patternProviders();

    /**
     * @return adaptive pattern-provider definition facet
     */
    @NotNull
    AdaptivePatternProviderRegistry adaptivePatternProviders();

    /**
     * @return Trinity pattern recipe-ID resolver facet
     */
    @NotNull
    TrinityPatternRecipeIdRegistry trinityPatternRecipes();

    /**
     * @return virtual crafting output declaration facet
     */
    @NotNull
    VirtualCraftingRegistry virtualCrafting();
}
