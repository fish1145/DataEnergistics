package com.fish_dan_.data_energistics.api.entrypoint;

import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.dynamic.DynamicCraftingOutputRegistry;
import com.fish_dan_.data_energistics.api.registry.machine.CraftingMachineRegistry;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdRegistry;
import com.fish_dan_.data_energistics.api.registry.reusable.ReusableInputRegistry;
import com.fish_dan_.data_energistics.api.registry.search.TrinityPatternSearchRegistry;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalRegistry;
import com.fish_dan_.data_energistics.api.registry.tower.energy.TowerEnergyIntegrationRegistry;
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

    /** Returns the transaction-local energy adapter registry; legacy registrars must opt into this facet. */
    default TowerEnergyIntegrationRegistry towerEnergyIntegrations() {
        throw new UnsupportedOperationException("This legacy registrar does not support tower energy integrations");
    }

    /**
     * @return universal-terminal declaration facet
     */
    UniversalTerminalRegistry universalTerminals();

    /**
     * @return pattern-provider lifecycle declaration facet
     */
    PatternProviderRegistry patternProviders();

    /**
     * Returns the complete external crafting-machine declaration facet.
     *
     * <p>
     * Capacity and pattern-upload declarations share the current plugin staging transaction. Register them during
     * the plugin callback; do not retain this facet after registration completes.
     * </p>
     */
    CraftingMachineRegistry craftingMachines();

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

    /**
     * Declares deterministic reusable-input rules during plugin registration. Legacy registrar implementations remain
     * binary compatible, but must explicitly implement this facet before accepting reusable-input declarations.
     *
     * @return transaction-local reusable-input registry
     */
    default ReusableInputRegistry reusableInputs() {
        throw new UnsupportedOperationException("This legacy registrar does not support reusable crafting inputs");
    }
}
