package com.fish_dan_.data_energistics.integration.viewer.jei.ingredient;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.gui.GenericStackDisplayHelper;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.DataReassemblerRecipeIngredientAdapter;

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
import java.util.stream.Stream;

/**
 * Gives the shared recipe UI native JEI item/fluid identities and AE2 wrapped-key lookup semantics.
 */
public final class JeiDataReassemblerIngredientAdapter
                                                       implements DataReassemblerRecipeIngredientAdapter {

    @Override
    public void registerItemSlot(ItemSlot element, IngredientIO role, List<ItemStack> candidates) {
        LDLibJEIPlugin.recipeIngredient(element, role, () -> toTypedItems(candidates));
        ItemSlot.JEISupport.recipeSlot(element, role, candidates::stream);
    }

    @Override
    public void registerGenericStackSlot(UIElement element, IngredientIO role, GenericStack stack) {
        element.style(style -> style.tooltips(GenericStackDisplayHelper.createAmountTooltip(stack)));
        registerResolvedGenericStackSlot(element, role, JeiGenericStackIngredientResolver.resolve(stack));
    }

    private static <I> void registerResolvedGenericStackSlot(
                                                             UIElement element,
                                                             IngredientIO role,
                                                             JeiGenericStackIngredientResolver.ResolvedIngredient<I> ingredient) {
        LDLibJEIPlugin.recipeIngredient(element, role, () -> List.of(toTypedGenericStack(ingredient)));
        LDLibJEIPlugin.recipeSlot(
                element,
                role,
                ingredient.type(),
                () -> Stream.of(ingredient.ingredient()),
                ignored -> {
                    // This element renders its one immutable GenericStack directly.
                });
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
