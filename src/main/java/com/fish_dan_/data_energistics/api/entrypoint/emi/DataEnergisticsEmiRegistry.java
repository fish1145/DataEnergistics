package com.fish_dan_.data_energistics.api.entrypoint.emi;

import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import org.jetbrains.annotations.NotNull;

/**
 * Registration-stage EMI surface exposed to Data Energistics integration plugins.
 */
public interface DataEnergisticsEmiRegistry {

    /**
     * Registers one typed EMI recipe handler.
     *
     * <p>
     * EMI permits several handlers for the same menu type, so Data Energistics keeps declaration ordering intact and
     * rejects only duplicate public registration IDs.
     * </p>
     *
     * @param registrationId stable public ID used to reject duplicate declarations
     * @param menuType       menu registration handled by {@code handler}
     * @param handler        typed EMI recipe handler
     * @param <T>            concrete menu type handled by the recipe handler
     */
    <T extends AbstractContainerMenu> void registerRecipeHandler(
            @NotNull ResourceLocation registrationId,
            @NotNull MenuType<T> menuType,
            @NotNull EmiRecipeHandler<T> handler);
}
