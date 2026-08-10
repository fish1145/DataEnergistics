package com.fish_dan_.data_energistics.client.emi.entrypoint;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;

/**
 * Frozen generic EMI recipe-handler declaration published by one optional integration plugin.
 *
 * @param registrationId stable public declaration ID
 * @param menuType       menu registration handled by the declaration
 * @param handler        typed EMI recipe handler
 * @param <T>            concrete menu type handled by the declaration
 */
record EmiRecipeHandlerRegistration<T extends AbstractContainerMenu>(
                                                                     ResourceLocation registrationId,
                                                                     MenuType<T> menuType,
                                                                     EmiRecipeHandler<T> handler) {

    /**
     * Attaches the typed handler to EMI's active registration lifecycle.
     *
     * @param registry active Data Energistics EMI registry
     */
    void register(EmiRegistry registry) {
        registry.addRecipeHandler(this.menuType, this.handler);
    }
}
