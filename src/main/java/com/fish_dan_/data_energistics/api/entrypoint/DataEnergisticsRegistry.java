package com.fish_dan_.data_energistics.api.entrypoint;

import com.fish_dan_.data_energistics.api.registry.provider.PatternProviderRegistry;
import com.fish_dan_.data_energistics.api.registry.terminal.UniversalTerminalRegistry;
import com.fish_dan_.data_energistics.api.registry.virtual.VirtualCraftingRegistry;

/**
 * Typed registry facets available to one Data Energistics plugin.
 *
 * <p>
 * All facets refer to the same staging transaction. A plugin can therefore register terminals, provider
 * integrations and virtual output adapters from one entrypoint without coordinating multiple annotations.
 * </p>
 */
public interface DataEnergisticsRegistry {

    /**
     * Returns the universal-terminal registration facet.
     *
     * @return terminal facet
     */
    UniversalTerminalRegistry universalTerminals();

    /**
     * Returns the pattern-provider registration facet.
     *
     * @return provider facet
     */
    PatternProviderRegistry patternProviders();

    /**
     * Returns the virtual crafting output registration facet.
     *
     * @return virtual-crafting facet
     */
    VirtualCraftingRegistry virtualCrafting();
}
