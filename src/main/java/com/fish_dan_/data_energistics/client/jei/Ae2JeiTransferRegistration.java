package com.fish_dan_.data_energistics.client.jei;

import com.fish_dan_.data_energistics.menu.universal.UniversalCraftingTermMenu;
import com.fish_dan_.data_energistics.menu.universal.UniversalPatternEncodingTermMenu;
import com.fish_dan_.data_energistics.registry.ModMenus;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import tamaized.ae2jeiintegration.integration.modules.jei.transfer.EncodePatternTransferHandler;
import tamaized.ae2jeiintegration.integration.modules.jei.transfer.UseCraftingRecipeTransfer;

/**
 * Links the optional AE2 JEI Integration handlers only after its mod id has been confirmed as loaded.
 */
final class Ae2JeiTransferRegistration {

    private Ae2JeiTransferRegistration() {}

    /**
     * Registers the add-on handlers used by the universal crafting and pattern terminals.
     */
    static void register(IRecipeTransferRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("AE2 JEI transfer registration cannot be null");
        }
        var transferHelper = registration.getTransferHelper();
        registration.addRecipeTransferHandler(
                new UseCraftingRecipeTransfer<>(
                        UniversalCraftingTermMenu.class,
                        ModMenus.UNIVERSAL_CRAFTING_TERM.get(),
                        transferHelper),
                RecipeTypes.CRAFTING);
        registration.addUniversalRecipeTransferHandler(new EncodePatternTransferHandler<>(
                ModMenus.UNIVERSAL_PATTERN_ENCODING_TERM.get(),
                UniversalPatternEncodingTermMenu.class,
                transferHelper,
                registration.getJeiHelpers().getIngredientVisibility()));
    }
}
