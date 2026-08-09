package com.fish_dan_.data_energistics.recipe.charger;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public record DataChargerRecipeInput(ItemStack stack) implements RecipeInput {

    @Override
    public ItemStack getItem(int index) {
        if (index != 0) {
            throw new IndexOutOfBoundsException(index);
        }
        return this.stack;
    }

    @Override
    public int size() {
        return 1;
    }
}
