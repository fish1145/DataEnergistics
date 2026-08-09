package com.fish_dan_.data_energistics.recipe.charger;

import com.fish_dan_.data_energistics.registry.ModRecipes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public final class DataChargerRecipe implements Recipe<DataChargerRecipeInput> {

    private final Ingredient ingredient;
    private final ItemStack result;
    private final long dataFlow;
    private final double power;

    public DataChargerRecipe(Ingredient ingredient, ItemStack result, long dataFlow, double power) {
        this.ingredient = ingredient;
        this.result = result.copy();
        this.dataFlow = dataFlow;
        this.power = power;
    }

    @Override
    public boolean matches(DataChargerRecipeInput input, Level level) {
        return this.ingredient.test(input.stack());
    }

    @Override
    public ItemStack assemble(DataChargerRecipeInput input, HolderLookup.Provider registries) {
        return this.getResultItem(registries).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, this.ingredient);
    }

    public Ingredient getIngredient() {
        return this.ingredient;
    }

    public ItemStack getResult() {
        return this.result.copy();
    }

    public long getDataFlow() {
        return this.dataFlow;
    }

    public double getPower() {
        return this.power;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.DATA_CHARGER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.DATA_CHARGER_TYPE.get();
    }
}
