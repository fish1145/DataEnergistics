package com.fish_dan_.data_energistics.integration.viewer.emi.ui;

import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;
import com.lowdragmc.lowdraglib2.integration.xei.emi.EMIRecipeSlotWidget;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.Bounds;
import org.joml.Matrix4f;

import java.util.function.BiPredicate;
import java.util.function.Supplier;

/**
 * Keeps EMI recipe context on logical outputs without marking recipe inputs as outputs.
 */
public final class DataReassemblerEmiRecipeSlotWidget extends EMIRecipeSlotWidget {

    private final IngredientIO role;

    public DataReassemblerEmiRecipeSlotWidget(
                                              IngredientIO role,
                                              Supplier<EmiIngredient> ingredientProvider,
                                              Supplier<Matrix4f> localToWorldSupplier,
                                              BiPredicate<Float, Float> isMouseOver,
                                              Supplier<Bounds> boundsProvider) {
        super(ingredientProvider, localToWorldSupplier, isMouseOver, boundsProvider);
        this.role = role;
    }

    @Override
    public DataReassemblerEmiRecipeSlotWidget recipeContext(EmiRecipe recipe) {
        if (role == IngredientIO.OUTPUT) {
            super.recipeContext(recipe);
        }
        return this;
    }
}
