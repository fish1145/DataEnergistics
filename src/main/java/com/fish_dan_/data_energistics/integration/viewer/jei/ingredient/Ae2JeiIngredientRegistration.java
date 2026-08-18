package com.fish_dan_.data_energistics.integration.viewer.jei.ingredient;

import com.fish_dan_.data_energistics.Data_Energistics;

import tamaized.ae2jeiintegration.api.integrations.jei.IngredientConverters;

/**
 * Registers Data Energistics custom JEI ingredients with the optional AE2 JEI Integration conversion registry.
 */
public final class Ae2JeiIngredientRegistration {

    private static boolean registered;

    private Ae2JeiIngredientRegistration() {}

    /**
     * Installs the converter once so AE2 JEI Integration can translate recipe slots and clickable ingredients.
     */
    public static synchronized void registerOnce() {
        if (registered) {
            return;
        }
        if (!IngredientConverters.register(DataResourceAe2JeiIngredientConverter.INSTANCE)) {
            String message = "An AE2 JEI Integration converter is already registered for Data Energistics resources";
            Data_Energistics.LOGGER.error(message);
            throw new IllegalStateException(message);
        }
        registered = true;
    }
}
