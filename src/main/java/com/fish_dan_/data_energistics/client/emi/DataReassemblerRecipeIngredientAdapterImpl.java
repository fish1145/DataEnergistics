package com.fish_dan_.data_energistics.client.emi;

import com.fish_dan_.data_energistics.client.recipe.DataReassemblerRecipeIngredientAdapter;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import com.lowdragmc.lowdraglib2.integration.xei.emi.LDLibEMIPlugin;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

import java.util.List;

/**
 * Gives the shared recipe UI native EMI identities, including a codec-backed fallback for custom AE keys.
 */
public final class DataReassemblerRecipeIngredientAdapterImpl
                                                              implements DataReassemblerRecipeIngredientAdapter {

    @Override
    public void registerItemSlot(ItemSlot element, IngredientIO role, List<ItemStack> candidates) {
        LDLibEMIPlugin.recipeIngredient(element, role, () -> List.of(toItemIngredient(candidates)));
        LDLibEMIPlugin.recipeSlot(element, () -> toItemIngredient(candidates));
    }

    @Override
    public void registerGenericStackSlot(UIElement element, IngredientIO role, GenericStack stack) {
        LDLibEMIPlugin.recipeIngredient(element, role, () -> List.of(toEmiStack(stack)));
        LDLibEMIPlugin.recipeSlot(element, () -> toEmiStack(stack));
    }

    private static EmiIngredient toItemIngredient(List<ItemStack> candidates) {
        return EmiIngredient.of(candidates.stream().map(stack -> EmiStack.of(stack.copy())).toList());
    }

    static EmiStack toEmiStack(GenericStack stack) {
        if (stack.what() instanceof AEFluidKey fluidKey) {
            int renderAmount = (int) Math.min(Integer.MAX_VALUE, stack.amount());
            FluidStack fluidStack = fluidKey.toStack(renderAmount);
            return EmiStack.of(
                    fluidStack.getFluid(),
                    fluidStack.getComponentsPatch(),
                    stack.amount());
        }

        return GenericAeKeyEmiStacks.toEmiStack(stack);
    }
}
