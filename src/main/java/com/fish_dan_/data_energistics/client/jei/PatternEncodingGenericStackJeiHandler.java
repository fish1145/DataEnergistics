package com.fish_dan_.data_energistics.client.jei;

import com.fish_dan_.data_energistics.client.jei.ingredient.DataResourceJeiIngredient;
import com.fish_dan_.data_energistics.client.screen.GenericStackLookupScreen;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.client.gui.StackWithBounds;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.runtime.IClickableIngredient;

import java.util.Optional;

final class PatternEncodingGenericStackJeiHandler<T extends AbstractContainerScreen<?>>
                                                 implements IGuiContainerHandler<T> {

    @Override
    public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
                                                                                        IClickableIngredientFactory builder, T containerScreen, double mouseX, double mouseY) {
        if (!(containerScreen instanceof GenericStackLookupScreen lookupScreen)) {
            return Optional.empty();
        }

        StackWithBounds hovered = lookupScreen.dataEnergistics$getGenericStackUnderMouse(mouseX, mouseY);
        if (hovered == null) {
            return Optional.empty();
        }

        GenericStack stack = hovered.stack();
        if (stack.what() instanceof AEFluidKey fluidKey) {
            FluidStack fluidStack = fluidKey.toStack((int) Math.max(1L, Math.min(Integer.MAX_VALUE, stack.amount())));
            return builder.createBuilder(NeoForgeTypes.FLUID_STACK, fluidStack).buildWithArea(hovered.bounds());
        }

        DataResourceJeiIngredient dataResourceIngredient = DataResourceJeiIngredient.from(stack);
        if (dataResourceIngredient != null) {
            return builder.createBuilder(DataResourceJeiIngredient.TYPE, dataResourceIngredient)
                    .buildWithArea(hovered.bounds());
        }

        return builder.createBuilder(stack.what().wrapForDisplayOrFilter()).buildWithArea(hovered.bounds());
    }
}
