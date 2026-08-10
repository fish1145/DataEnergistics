package com.fish_dan_.data_energistics.api.entrypoint.jei;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import mezz.jei.api.recipe.RecipeType;
import org.jetbrains.annotations.NotNull;

/**
 * Registration-stage JEI surface exposed to Data Energistics integration plugins.
 */
public interface DataEnergisticsJeiRegistry {

    /**
     * Registers one category-specific JEI recipe-transfer handler.
     *
     * <p>
     * The factory is deferred until Data Energistics reaches JEI's transfer-registration phase, where JEI supplies
     * the scoped {@code IRecipeTransferHandlerHelper}. The declared menu and recipe types are validated before the
     * frozen registration is published, so plugins cannot accidentally claim the same transfer target.
     * </p>
     *
     * @param registrationId stable public ID used to reject duplicate declarations
     * @param menuClass      exact open-menu class handled by the transfer
     * @param menuType       menu registration matching {@code menuClass}
     * @param recipeType     JEI category handled by the transfer
     * @param factory        creates the typed transfer handler for JEI's active registration cycle
     * @param <T>            concrete menu type handled by the transfer
     * @param <R>            JEI recipe-view type handled by the transfer
     */
    <T extends AbstractContainerMenu, R> void registerRecipeTransferHandler(
                                                                            @NotNull ResourceLocation registrationId,
                                                                            @NotNull Class<T> menuClass,
                                                                            @NotNull MenuType<T> menuType,
                                                                            @NotNull RecipeType<R> recipeType,
                                                                            @NotNull JeiRecipeTransferHandlerFactory<T, R> factory);
}
