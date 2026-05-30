package com.fish_dan_.data_energistics.recipe;

import com.fish_dan_.data_energistics.registry.ModRecipes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public final class TimeShiftRecipe implements Recipe<TimeShiftRecipeInput> {

    public static final int TICKS_PER_MINUTE = 20 * 60;

    private final NonNullList<TimeShiftIngredient> itemInputs;
    private final NonNullList<Ingredient> ingredients;
    private final NonNullList<ItemStack> results;
    private final int durationTicks;
    private final TimeShiftTimeCondition timeCondition;

    public TimeShiftRecipe(List<TimeShiftIngredient> itemInputs, List<ItemStack> results, int durationTicks, TimeShiftTimeCondition timeCondition) {
        this.itemInputs = NonNullList.copyOf(itemInputs);
        this.ingredients = expandIngredients(itemInputs);
        this.results = NonNullList.copyOf(results);
        this.durationTicks = Math.max(1, durationTicks);
        this.timeCondition = timeCondition;
    }

    public TimeShiftRecipe(List<TimeShiftIngredient> itemInputs, List<ItemStack> results, double minutes, TimeShiftTimeCondition timeCondition) {
        this(itemInputs, results, (int) Math.ceil(minutes * TICKS_PER_MINUTE), timeCondition);
    }

    @Override
    public boolean matches(TimeShiftRecipeInput input, Level level) {
        if (!this.canRunAt(level)) {
            return false;
        }

        if (getTotalItemCount(input.items()) < this.ingredients.size()) {
            return false;
        }

        NonNullList<Ingredient> remaining = NonNullList.copyOf(this.ingredients);
        for (ItemStack stack : input.items()) {
            if (stack.isEmpty()) {
                continue;
            }

            int available = stack.getCount();
            var iterator = remaining.iterator();
            while (iterator.hasNext() && available > 0) {
                if (iterator.next().test(stack)) {
                    iterator.remove();
                    available--;
                }
            }

            if (remaining.isEmpty()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public ItemStack assemble(TimeShiftRecipeInput input, HolderLookup.Provider registries) {
        return this.getResultItem(registries).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.results.isEmpty() ? ItemStack.EMPTY : this.results.getFirst();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.ingredients;
    }

    public NonNullList<TimeShiftIngredient> getItemInputs() {
        return this.itemInputs;
    }

    public NonNullList<ItemStack> getResults() {
        return this.results;
    }

    public int getDurationTicks() {
        return this.durationTicks;
    }

    public double getDurationMinutes() {
        return (double) this.durationTicks / TICKS_PER_MINUTE;
    }

    public TimeShiftTimeCondition getTimeCondition() {
        return this.timeCondition;
    }

    public boolean canRunAt(Level level) {
        return this.timeCondition.matches(level);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.TIME_SHIFT_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.TIME_SHIFT_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    private static NonNullList<Ingredient> expandIngredients(List<TimeShiftIngredient> itemInputs) {
        List<Ingredient> expanded = new ArrayList<>();
        for (TimeShiftIngredient input : itemInputs) {
            for (int i = 0; i < input.count(); i++) {
                expanded.add(input.ingredient());
            }
        }
        return NonNullList.copyOf(expanded);
    }

    private static int getTotalItemCount(List<ItemStack> items) {
        int total = 0;
        for (ItemStack stack : items) {
            total += stack.getCount();
        }
        return total;
    }
}
