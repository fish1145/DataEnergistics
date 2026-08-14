package com.fish_dan_.data_energistics.api.entrypoint;

import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.dynamic.DynamicCraftingOutputRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdRegistry;
import com.fish_dan_.data_energistics.api.registry.search.TrinityPatternSearchRegistry;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalRegistry;
import com.fish_dan_.data_energistics.api.registry.virtual.VirtualCraftingRegistry;

/**
 * Root registration-stage surface passed to a Data Energistics plugin.
 *
 * <p>
 * All facets refer to the same staging transaction. A plugin can therefore register terminals, provider
 * integrations and crafting-output adapters from one entrypoint without coordinating multiple annotations.
 * </p>
 */
public interface DataEnergisticsRegistry {

    /**
     * @return universal-terminal declaration facet
     */
    UniversalTerminalRegistry universalTerminals();

    /**
     * @return pattern-provider lifecycle declaration facet
     */
    PatternProviderRegistry patternProviders();

    /**
     * @return adaptive pattern-provider definition facet
     */
    AdaptivePatternProviderRegistry adaptivePatternProviders();

    /**
     * @return Trinity pattern recipe-ID resolver facet
     */
    TrinityPatternRecipeIdRegistry trinityPatternRecipes();

    /**
     * @return Trinity pattern search contribution facet
     */
    TrinityPatternSearchRegistry trinityPatternSearch();

    /**
     * @return virtual crafting output declaration facet
     */
    VirtualCraftingRegistry virtualCrafting();

    /**
     * @return dynamic physical crafting-output declaration facet
     */
    DynamicCraftingOutputRegistry dynamicCraftingOutputs();
}
