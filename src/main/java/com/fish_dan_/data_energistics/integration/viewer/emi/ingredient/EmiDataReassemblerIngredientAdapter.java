package com.fish_dan_.data_energistics.integration.viewer.emi.ingredient;

import com.fish_dan_.data_energistics.integration.viewer.emi.ui.DataReassemblerEmiRecipeSlotWidget;
import com.fish_dan_.data_energistics.integration.viewer.xei.recipe.DataReassemblerRecipeIngredientAdapter;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import com.lowdragmc.lowdraglib2.integration.xei.emi.EMIUIEvents;
import com.lowdragmc.lowdraglib2.integration.xei.emi.LDLibEMIPlugin;
import com.lowdragmc.lowdraglib2.integration.xei.emi.handler.EMIRecipeWidgetHandler;

import appeng.api.stacks.GenericStack;

import net.minecraft.world.item.ItemStack;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

import java.util.List;
import java.util.function.Supplier;

/**
 * Gives the shared recipe UI native EMI identities for fluids, Data, and DataFlow.
 */
public final class EmiDataReassemblerIngredientAdapter
                                                       implements DataReassemblerRecipeIngredientAdapter {

    @Override
    public void registerItemSlot(ItemSlot element, IngredientIO role, List<ItemStack> candidates) {
        LDLibEMIPlugin.recipeIngredient(element, role, () -> List.of(toItemIngredient(candidates)));
        registerRecipeSlot(element, role, () -> toItemIngredient(candidates));
    }

    @Override
    public void registerGenericStackSlot(UIElement element, IngredientIO role, GenericStack stack) {
        LDLibEMIPlugin.recipeIngredient(element, role, () -> List.of(toEmiStack(stack)));
        registerRecipeSlot(element, role, () -> toEmiStack(stack));
    }

    private static void registerRecipeSlot(
                                           UIElement element, IngredientIO role, Supplier<EmiIngredient> displayIngredient) {
        element.addEventListener(EMIUIEvents.RECIPE_WIDGET, event -> {
            if (event.customData instanceof EMIRecipeWidgetHandler recipeSlot) {
                var slot = new DataReassemblerEmiRecipeSlotWidget(
                        role,
                        displayIngredient,
                        recipeSlot.localToWorld,
                        element::isMouseOverElement,
                        () -> LDLibEMIPlugin.getBounds(element));
                for (var component : element.getStyle().tooltips().asList()) {
                    slot.appendTooltip(component);
                }
                recipeSlot.addWidget(slot);
            }
        });
        element.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            event.hasHandler = false;
            event.stopImmediatePropagation();
        });
    }

    private static EmiIngredient toItemIngredient(List<ItemStack> candidates) {
        return EmiIngredient.of(
                candidates.stream().map(stack -> EmiStack.of(stack.copy())).toList(),
                candidates.getFirst().getCount());
    }

    static EmiStack toEmiStack(GenericStack stack) {
        return EmiGenericStackIngredientResolver.resolve(stack);
    }
}
