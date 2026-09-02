package com.fish_dan_.data_energistics.integration.viewer.emi.ingredient;

import com.fish_dan_.data_energistics.client.crafting.tree.viewer.CraftingPlanIngredientViewer;

import appeng.api.stacks.GenericStack;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.integration.xei.emi.EMIUIEvents;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.runtime.EmiReloadManager;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/** Native EMI graph navigation; instances do not retain a menu, graph, or reload-specific recipe manager. */
public final class CraftingPlanEmiIngredientViewer implements CraftingPlanIngredientViewer {

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public boolean available() {
        return EmiReloadManager.isLoaded();
    }

    @Override
    public void bind(UIElement canvas, Supplier<@Nullable GenericStack> hovered) {
        canvas.addEventListener(EMIUIEvents.STACK_PROVIDER, event -> {
            GenericStack stack = available() ? hovered.get() : null;
            event.customData = stack == null ? EmiStackInteraction.EMPTY : new EmiStackInteraction(EmiGenericStackIngredientResolver.resolve(stack), null, true);
        });
    }

    @Override
    public boolean show(GenericStack stack, boolean recipes) {
        if (!available()) return false;
        var ingredient = EmiGenericStackIngredientResolver.resolve(stack);
        if (recipes) EmiApi.displayRecipes(ingredient);
        else EmiApi.displayUses(ingredient);
        return true;
    }
}
