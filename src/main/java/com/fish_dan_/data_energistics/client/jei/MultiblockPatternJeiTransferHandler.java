package com.fish_dan_.data_energistics.client.jei;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewMaterial;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockRecipeView;
import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockRecipeViewSource;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingMultiblockTransferTarget;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

import appeng.util.ConfigInventory;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Transfers the live multiblock ordinary-recipe projection into one exact AE pattern-encoding menu type.
 *
 * <p>
 * JEI slot views are deliberately ignored because the draggable preview can change after JEI caches those slots.
 * The live typed source is sampled once per transfer attempt and the menu's real configuration inventories define
 * capacity.
 * </p>
 */
final class MultiblockPatternJeiTransferHandler<T extends AbstractContainerMenu, R extends MultiblockRecipeViewSource>
                                               implements IRecipeTransferHandler<T, R> {

    private final Class<T> menuClass;
    private final MenuType<T> menuType;
    private final RecipeType<R> recipeType;
    private final IRecipeTransferHandlerHelper transferHelper;

    /**
     * Creates a production handler backed by JEI's user-visible transfer error factory.
     */
    MultiblockPatternJeiTransferHandler(Class<T> menuClass,
                                        MenuType<T> menuType,
                                        RecipeType<R> recipeType,
                                        IRecipeTransferHandlerHelper transferHelper) {
        this.menuClass = menuClass;
        this.menuType = menuType;
        this.recipeType = recipeType;
        this.transferHelper = transferHelper;
    }

    @Override
    public Class<? extends T> getContainerClass() {
        return this.menuClass;
    }

    @Override
    public Optional<MenuType<T>> getMenuType() {
        return Optional.of(this.menuType);
    }

    @Override
    public RecipeType<R> getRecipeType() {
        return this.recipeType;
    }

    @Override
    @Nullable
    public IRecipeTransferError transferRecipe(T menu,
                                               R recipe,
                                               IRecipeSlotsView recipeSlots,
                                               Player player,
                                               boolean maxTransfer,
                                               boolean doTransfer) {
        MultiblockRecipeView view;
        try {
            ResourceLocation registeredRecipeId = recipe.registeredRecipeId();
            view = recipe.currentRecipeView();
            if (!registeredRecipeId.equals(view.registeredRecipeId())) {
                Data_Energistics.LOGGER.warn(
                        "Rejected stale JEI multiblock recipe identity: registered={}, live={}",
                        registeredRecipeId,
                        view.registeredRecipeId());
                return userError("The multiblock preview changed. Reopen its recipe page and try again.");
            }
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.warn("Rejected unavailable JEI multiblock recipe transfer", exception);
            return userError("The multiblock preview changed. Reopen its recipe page and try again.");
        }
        if (!(menu instanceof PatternEncodingMultiblockTransferTarget target)) {
            Data_Energistics.LOGGER.error(
                    "JEI multiblock transfer menu {} does not expose the pattern-encoding transfer target",
                    menu.getClass().getName());
            return userError("This pattern terminal cannot accept multiblock recipes.");
        }

        CapacityError capacityError;
        try {
            ConfigInventory inputInventory = target.data_energistics$getMultiblockTransferInputInventory();
            ConfigInventory outputInventory = target.data_energistics$getMultiblockTransferOutputInventory();
            capacityError = findCapacityError(
                    view.inputs().size(),
                    1,
                    inputInventory.size(),
                    outputInventory.size());
            if (capacityError == null) {
                String materialError = findMaterialError("input", inputInventory, view.inputs());
                if (materialError == null) {
                    materialError = findMaterialError("output", outputInventory, List.of(view.output()));
                }
                if (materialError != null) {
                    return userError(materialError);
                }
            }
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Failed to inspect JEI multiblock transfer capacity for menu {}",
                    menu.getClass().getName(),
                    exception);
            return userError("This pattern terminal cannot inspect its processing slot capacity.");
        }
        if (capacityError != null) {
            return userError(capacityError.message());
        }

        if (doTransfer) {
            try {
                target.data_energistics$requestMultiblockTransfer(view);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error(
                        "Failed to request JEI multiblock transfer for menu {}",
                        menu.getClass().getName(),
                        exception);
                return userError("The multiblock recipe transfer request failed.");
            }
        }
        return null;
    }

    @Nullable
    private static String findMaterialError(String role,
                                            ConfigInventory inventory,
                                            List<PreviewMaterial> materials) {
        for (int slot = 0; slot < materials.size(); slot++) {
            PreviewMaterial material = materials.get(slot);
            if (!inventory.isAllowedIn(slot, material.key())) {
                return "This pattern terminal rejects multiblock " + role + " slot " + (slot + 1) + ".";
            }
            long maximum = inventory.getMaxAmount(material.key());
            if (maximum <= 0L || material.amount() > maximum) {
                return "Multiblock " + role + " slot " + (slot + 1) +
                        " exceeds this pattern terminal's amount limit.";
            }
        }
        return null;
    }

    /**
     * Returns the first atomic capacity failure without truncating either side of the ordinary recipe.
     */
    @Nullable
    private static CapacityError findCapacityError(int inputCount,
                                                   int outputCount,
                                                   int inputCapacity,
                                                   int outputCapacity) {
        if (inputCount > inputCapacity) {
            return new CapacityError(CapacityKind.INPUT, inputCount, inputCapacity);
        }
        if (outputCount > outputCapacity) {
            return new CapacityError(CapacityKind.OUTPUT, outputCount, outputCapacity);
        }
        return null;
    }

    private IRecipeTransferError userError(String message) {
        return this.transferHelper.createUserErrorWithTooltip(Component.literal(message));
    }

    /**
     * Side of the AE processing configuration that cannot hold the complete live recipe.
     */
    enum CapacityKind {
        INPUT,
        OUTPUT
    }

    /** Exact capacity mismatch for the current menu and live recipe view. */
    private record CapacityError(CapacityKind kind, int required, int available) {

        String message() {
            String side = this.kind == CapacityKind.INPUT ? "input" : "output";
            return "This pattern terminal has " + this.available + " processing " + side +
                    " slots, but the multiblock recipe requires " + this.required + ".";
        }
    }
}
