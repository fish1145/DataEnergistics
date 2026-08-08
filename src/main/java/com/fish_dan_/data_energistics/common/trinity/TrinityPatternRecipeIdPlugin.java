package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsEntrypoint;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsPlugin;
import com.fish_dan_.data_energistics.api.entrypoint.DataEnergisticsRegistry;
import com.fish_dan_.data_energistics.api.registry.recipe.TrinityPatternRecipeIdResolver;

/**
 * Registers AE2's built-in recipe identities through the unified plugin transaction.
 */
@DataEnergisticsEntrypoint
public final class TrinityPatternRecipeIdPlugin implements DataEnergisticsPlugin {

    /**
     * Public constructor required by the common entrypoint scanner.
     */
    public TrinityPatternRecipeIdPlugin() {}

    @Override
    public void register(DataEnergisticsRegistry registry) {
        for (TrinityPatternRecipeIdResolver resolver : TrinityPatternRecipeIdResolvers.builtIns()) {
            registry.trinityPatternRecipes().register(resolver);
        }
    }
}
