package com.fish_dan_.data_energistics.api.entrypoint.jei;

import net.minecraft.world.inventory.AbstractContainerMenu;

import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import org.jetbrains.annotations.NotNull;

/**
 * Creates a typed JEI recipe-transfer handler after JEI has opened its registration lifecycle.
 *
 * @param <T> concrete menu type handled by the transfer
 * @param <R> JEI recipe-view type handled by the transfer
 */
@FunctionalInterface
public interface JeiRecipeTransferHandlerFactory<T extends AbstractContainerMenu, R> {

    /**
     * Creates the handler using JEI's error factory for the active registration cycle.
     *
     * @param transferHelper JEI helper that creates user-facing transfer errors
     * @return non-null handler matching the menu and recipe types declared to the registry
     */
    @NotNull
    IRecipeTransferHandler<T, R> create(@NotNull IRecipeTransferHandlerHelper transferHelper);
}
