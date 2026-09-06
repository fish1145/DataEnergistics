package com.fish_dan_.data_energistics.api.entrypoint;

import com.fish_dan_.data_energistics.api.registry.adaptive.AdaptivePatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.dynamic.DynamicCraftingOutputRegistry;
import com.fish_dan_.data_energistics.api.registry.machine.CraftingMachineRegistry;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacityRegistration;
import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacityRegistry;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationRegistration;
import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdRegistry;
import com.fish_dan_.data_energistics.api.registry.reusable.ReusableInputRegistry;
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
     * @return 3.2 capacity-only machine declaration facet
     *
     * @deprecated Use {@link #craftingMachines()}; removed in 3.3.0.
     */
    @Deprecated(since = "3.2.0")
    CraftingMachineCapacityRegistry craftingMachineCapacities();

    /**
     * Returns the complete external crafting-machine declaration facet.
     *
     * <p>
     * Data Energistics overrides this method with the shared staging registry. The default keeps third-party 3.2
     * registrar implementations source-compatible for capacity declarations; those legacy implementations cannot
     * accept the newly added upload capability.
     * </p>
     */
    default CraftingMachineRegistry craftingMachines() {
        CraftingMachineCapacityRegistry capacities = this.craftingMachineCapacities();
        return new CraftingMachineRegistry() {

            @Override
            public void registerCapacity(CraftingMachineCapacityRegistration registration) {
                capacities.register(registration);
            }

            @Override
            public void registerPatternUploadWorkstation(PatternUploadWorkstationRegistration registration) {
                throw new UnsupportedOperationException(
                        "This legacy Data Energistics registrar does not support pattern upload workstations");
            }
        };
    }

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
