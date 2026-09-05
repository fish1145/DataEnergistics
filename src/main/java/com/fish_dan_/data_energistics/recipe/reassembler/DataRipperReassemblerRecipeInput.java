package com.fish_dan_.data_energistics.recipe.reassembler;

import appeng.api.stacks.GenericStack;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.List;

public record DataRipperReassemblerRecipeInput(List<ItemStack> items, List<GenericStack> fluidInputs,
                                               List<GenericStack> keyInputs)
        implements RecipeInput {

    @Override
    public ItemStack getItem(int index) {
        return this.items.get(index);
    }

    @Override
    public int size() {
        return this.items.size();
    }
}
