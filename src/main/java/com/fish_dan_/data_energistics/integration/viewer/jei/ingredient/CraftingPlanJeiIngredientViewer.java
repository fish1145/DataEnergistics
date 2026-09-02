package com.fish_dan_.data_energistics.integration.viewer.jei.ingredient;

import com.fish_dan_.data_energistics.client.crafting.tree.viewer.CraftingPlanIngredientViewer;
import com.fish_dan_.data_energistics.client.screen.GenericStackLookupScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;

import appeng.api.stacks.GenericStack;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.integration.xei.jei.JEIUIEvents;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IJeiRuntime;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/** Native JEI graph navigation using the plugin's current runtime, never a stale retained runtime instance. */
public final class CraftingPlanJeiIngredientViewer implements CraftingPlanIngredientViewer {
    private final Supplier<@Nullable IJeiRuntime> runtime;

    public CraftingPlanJeiIngredientViewer(Supplier<@Nullable IJeiRuntime> runtime) {
        this.runtime = runtime;
    }

    @Override
    public int priority() { return 10; }

    @Override
    public boolean available() { return this.runtime.get() != null; }

    @Override
    public void bind(UIElement canvas, Supplier<@Nullable GenericStack> hovered) {
        canvas.addEventListener(JEIUIEvents.CLICKABLE_INGREDIENT, event -> {
            if (!(event.customData instanceof IClickableIngredientFactory factory)) return;
            GenericStack stack = available() ? hovered.get() : null;
            if (stack == null || !(Minecraft.getInstance().screen instanceof GenericStackLookupScreen screen)) {
                event.customData = Optional.empty();
                return;
            }
            var bounds = screen.dataEnergistics$getGenericStackUnderMouse(event.x, event.y);
            event.customData = bounds == null ? Optional.empty()
                    : clickable(factory, JeiGenericStackIngredientResolver.resolve(stack), bounds.bounds());
        });
    }

    @Override
    public boolean show(GenericStack stack, boolean recipes) {
        IJeiRuntime current = this.runtime.get();
        if (current == null) return false;
        show(current, JeiGenericStackIngredientResolver.resolve(stack), recipes);
        return true;
    }

    private static <T> Optional<? extends IClickableIngredient<?>> clickable(IClickableIngredientFactory factory,
            JeiGenericStackIngredientResolver.ResolvedIngredient<T> ingredient, Rect2i bounds) {
        return factory.createBuilder(ingredient.type(), ingredient.ingredient()).buildWithArea(bounds);
    }

    private static <T> void show(IJeiRuntime runtime, JeiGenericStackIngredientResolver.ResolvedIngredient<T> ingredient,
                                 boolean recipes) {
        var role = recipes ? RecipeIngredientRole.OUTPUT : RecipeIngredientRole.INPUT;
        runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory()
                .createFocus(role, ingredient.type(), ingredient.ingredient()));
    }
}
