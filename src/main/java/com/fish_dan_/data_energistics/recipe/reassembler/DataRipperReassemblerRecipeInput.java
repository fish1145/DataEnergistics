package com.fish_dan_.data_energistics.recipe.reassembler;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import appeng.api.stacks.GenericStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record DataRipperReassemblerRecipeInput(List<ItemStack> items, List<GenericStack> fluidInputs,
                                               @Nullable GenericStack keyInput)
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
