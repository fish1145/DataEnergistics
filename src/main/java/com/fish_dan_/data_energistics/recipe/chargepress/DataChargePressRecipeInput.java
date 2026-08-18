package com.fish_dan_.data_energistics.recipe.chargepress;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/** Runtime inputs for a data charge press recipe. */
public record DataChargePressRecipeInput(List<ItemStack> items, FluidStack fluid, ItemStack catalyst)
        implements RecipeInput {

    public DataChargePressRecipeInput {
        items = List.copyOf(items);
        fluid = fluid.copy();
        catalyst = catalyst.copy();
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
