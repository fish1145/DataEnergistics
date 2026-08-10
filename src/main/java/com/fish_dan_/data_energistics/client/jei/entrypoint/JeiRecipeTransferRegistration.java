package com.fish_dan_.data_energistics.client.jei.entrypoint;

import com.fish_dan_.data_energistics.api.entrypoint.jei.JeiRecipeTransferHandlerFactory;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.registration.IRecipeTransferRegistration;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.Optional;

/**
 * Frozen generic JEI transfer declaration published by one optional integration plugin.
 *
 * @param registrationId stable public declaration ID
 * @param menuClass      exact menu class handled by the declaration
 * @param menuType       exact registered menu type handled by the declaration
 * @param recipeType     JEI category handled by the declaration
 * @param factory        creates the typed handler when JEI exposes its transfer helper
 * @param <T>            concrete menu type handled by the declaration
 * @param <R>            JEI recipe-view type handled by the declaration
 */
record JeiRecipeTransferRegistration<T extends AbstractContainerMenu, R>(
        ResourceLocation registrationId,
        Class<T> menuClass,
        MenuType<T> menuType,
        RecipeType<R> recipeType,
        JeiRecipeTransferHandlerFactory<T, R> factory) {

    /**
     * Creates and validates the external handler before attaching it to JEI's active transfer registry.
     *
     * @param registration active JEI transfer registration lifecycle
     */
    void register(IRecipeTransferRegistration registration) {
        IRecipeTransferHandler<T, R> handler = factory.create(registration.getTransferHelper());
        if (!this.menuClass.equals(handler.getContainerClass())) {
            throw new IllegalStateException(
                    "JEI transfer handler '" + this.registrationId + "' returned a different menu class");
        }
        if (!handler.getMenuType().equals(Optional.of(this.menuType))) {
            throw new IllegalStateException(
                    "JEI transfer handler '" + this.registrationId + "' returned a different menu type");
        }
        if (!this.recipeType.equals(handler.getRecipeType())) {
            throw new IllegalStateException(
                    "JEI transfer handler '" + this.registrationId + "' returned a different recipe type");
        }
        registration.addRecipeTransferHandler(handler, this.recipeType);
    }
}
