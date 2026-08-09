package com.fish_dan_.data_energistics.client.jei;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.client.recipe.DataReassemblerRecipeIngredientAdapter;

import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.GenericStack;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import com.lowdragmc.lowdraglib2.integration.xei.jei.LDLibJEIPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives the shared recipe UI native JEI item/fluid identities and AE2 wrapped-key lookup semantics.
 */
public final class JeiDataReassemblerIngredientAdapter
                                                       implements DataReassemblerRecipeIngredientAdapter {

    @Override
    public void registerItemSlot(ItemSlot element, IngredientIO role, List<ItemStack> candidates) {
        LDLibJEIPlugin.recipeIngredient(element, role, () -> toTypedItems(candidates));
        LDLibJEIPlugin.recipeSlot(
                element,
                () -> toTypedItem(element.getValue()),
                () -> toTypedItems(candidates));
    }

    @Override
    public void registerGenericStackSlot(UIElement element, IngredientIO role, GenericStack stack) {
        element.style(style -> style.tooltips(GenericStackDisplayHelper.createAmountTooltip(stack)));
        LDLibJEIPlugin.recipeIngredient(element, role, () -> List.of(toTypedGenericStack(stack)));
        LDLibJEIPlugin.recipeSlot(
                element,
                () -> toTypedGenericStack(stack),
                () -> List.of(toTypedGenericStack(stack)));
    }

    private static ITypedIngredient<?> toTypedItem(ItemStack stack) {
        return LDLibJEIPlugin.createTypedIngredient(VanillaTypes.ITEM_STACK, stack.copy())
                .orElseThrow(() -> rejectedIngredient("item", stack));
    }

    private static List<ITypedIngredient<?>> toTypedItems(List<ItemStack> candidates) {
        List<ITypedIngredient<?>> typedIngredients = new ArrayList<>(candidates.size());
        for (ItemStack candidate : candidates) {
            typedIngredients.add(toTypedItem(candidate));
        }
        return typedIngredients;
    }

    private static ITypedIngredient<?> toTypedGenericStack(GenericStack stack) {
        return toTypedGenericStack(JeiGenericStackIngredientResolver.resolve(stack));
    }

    private static <I> ITypedIngredient<?> toTypedGenericStack(
                                                               JeiGenericStackIngredientResolver.ResolvedIngredient<I> ingredient) {
        return LDLibJEIPlugin.createTypedIngredient(ingredient.type(), ingredient.ingredient())
                .orElseThrow(() -> rejectedIngredient("generic stack", ingredient.ingredient()));
    }

    private static IllegalStateException rejectedIngredient(String type, Object ingredient) {
        Data_Energistics.LOGGER.error("JEI rejected a data reassembler {} ingredient: {}", type, ingredient);
        return new IllegalStateException("JEI rejected a data reassembler " + type + " ingredient: " + ingredient);
    }
}
