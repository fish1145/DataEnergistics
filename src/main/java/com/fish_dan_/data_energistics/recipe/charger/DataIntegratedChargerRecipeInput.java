package com.fish_dan_.data_energistics.recipe.charger;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.List;

/** Runtime item inputs for a data integrated charger recipe. */
public record DataIntegratedChargerRecipeInput(List<ItemStack> items) implements RecipeInput {

    public DataIntegratedChargerRecipeInput {
        items = List.copyOf(items);
    }

    @Override
    public ItemStack getItem(int index) {
        return this.items.get(index);
    }

    @Override
    public int size() {
        return this.items.size();
    }
}
