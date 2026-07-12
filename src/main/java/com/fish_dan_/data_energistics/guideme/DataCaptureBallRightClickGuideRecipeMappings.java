package com.fish_dan_.data_energistics.guideme;

import com.fish_dan_.data_energistics.item.DataCaptureBallItem;
import com.fish_dan_.data_energistics.recipe.DataCaptureBallRightClickRecipe;
import com.fish_dan_.data_energistics.registry.ModRecipes;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import guideme.color.SymbolicColor;
import guideme.compiler.tags.RecipeTypeMappingSupplier;
import guideme.document.block.LytParagraph;
import guideme.document.block.LytSlotGrid;
import guideme.document.block.recipes.LytStandardRecipeBox;

import java.util.List;
import java.util.Locale;

public final class DataCaptureBallRightClickGuideRecipeMappings implements RecipeTypeMappingSupplier {

    @Override
    public void collect(RecipeTypeMappings mappings) {
        mappings.add(ModRecipes.DATA_CAPTURE_BALL_RIGHT_CLICK_TYPE.get(),
                DataCaptureBallRightClickGuideRecipeMappings::createRecipe);
    }

    private static LytStandardRecipeBox<DataCaptureBallRightClickRecipe> createRecipe(
                                                                                      RecipeHolder<DataCaptureBallRightClickRecipe> holder) {
        var recipe = holder.value();
        var details = LytParagraph.of(buildDetails(recipe));
        details.modifyStyle(style -> style.color(SymbolicColor.CRAFTING_RECIPE_TYPE));

        return LytStandardRecipeBox.builder()
                .title("Right Click")
                .icon(recipe.getItemIngredient().getItems()[0].getItem())
                .input(LytSlotGrid.row(List.of(
                        getItemInput(recipe),
                        Ingredient.of(new ItemStack(recipe.getInputBlock()))), true))
                .output(LytSlotGrid.rowFromStacks(List.of(new ItemStack(recipe.getResultBlock())), true))
                .addBottom(details)
                .build(holder);
    }

    private static Ingredient getItemInput(DataCaptureBallRightClickRecipe recipe) {
        ItemStack[] itemStacks = recipe.getItemIngredient().getItems();
        if (itemStacks.length == 1 && itemStacks[0].getItem() instanceof DataCaptureBallItem) {
            return Ingredient.of(DataCaptureBallItem.createConfiguredStack(recipe.getEnergyCost(), recipe.getDataCost()));
        }
        return recipe.getItemIngredient();
    }

    private static String buildDetails(DataCaptureBallRightClickRecipe recipe) {
        return String.format(
                Locale.ROOT,
                "Right Click | %d Data | %s AE",
                recipe.getDataCost(),
                formatEnergy(recipe.getEnergyCost()));
    }

    private static String formatEnergy(double energy) {
        if (energy == Math.rint(energy)) {
            return Long.toString((long) energy);
        }
        return Double.toString(energy);
    }
}
