package com.fish_dan_.data_energistics.integration.guideme;

import com.fish_dan_.data_energistics.recipe.timeshift.TimeShiftIngredient;
import com.fish_dan_.data_energistics.recipe.timeshift.TimeShiftRecipe;
import com.fish_dan_.data_energistics.recipe.timeshift.TimeShiftTimeCondition;
import com.fish_dan_.data_energistics.registry.DERecipes;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import guideme.color.SymbolicColor;
import guideme.compiler.tags.RecipeTypeMappingSupplier;
import guideme.document.block.LytParagraph;
import guideme.document.block.LytSlotGrid;
import guideme.document.block.recipes.LytStandardRecipeBox;

import java.util.Arrays;
import java.util.Locale;

public final class TimeShiftGuideRecipeMappings implements RecipeTypeMappingSupplier {

    @Override
    public void collect(RecipeTypeMappings mappings) {
        mappings.add(DERecipes.TIME_SHIFT_TYPE.get(), TimeShiftGuideRecipeMappings::createTimeShiftRecipe);
    }

    private static LytStandardRecipeBox<TimeShiftRecipe> createTimeShiftRecipe(RecipeHolder<TimeShiftRecipe> holder) {
        var recipe = holder.value();
        var details = LytParagraph.of(buildDetails(recipe));
        details.modifyStyle(style -> style.color(SymbolicColor.CRAFTING_RECIPE_TYPE));

        return LytStandardRecipeBox.builder()
                .title("Time Shift")
                .icon(Items.CLOCK)
                .input(LytSlotGrid.row(recipe.getItemInputs().stream().map(TimeShiftGuideRecipeMappings::withCount).toList(), true))
                .output(LytSlotGrid.rowFromStacks(recipe.getResults(), true))
                .addBottom(details)
                .build(holder);
    }

    private static String buildDetails(TimeShiftRecipe recipe) {
        return String.format(
                Locale.ROOT,
                "Duration: %.1f min | Time: %s",
                recipe.getDurationMinutes(),
                formatTimeCondition(recipe.getTimeCondition()));
    }

    private static String formatTimeCondition(TimeShiftTimeCondition condition) {
        return switch (condition) {
            case ALL -> "All";
            case DAY -> "Day";
            case NIGHT -> "Night";
        };
    }

    private static Ingredient withCount(TimeShiftIngredient ingredient) {
        return Ingredient.of(Arrays.stream(ingredient.ingredient().getItems()).map(stack -> {
            ItemStack copy = stack.copy();
            copy.setCount(ingredient.count());
            return copy;
        }));
    }
}
