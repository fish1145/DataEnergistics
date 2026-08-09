package com.fish_dan_.data_energistics.recipe.captureball;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.block.state.BlockState;

public record DataCaptureBallRightClickRecipeInput(ItemStack stack, BlockState state) implements RecipeInput {

    @Override
    public ItemStack getItem(int index) {
        return index == 0 ? this.stack : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return 1;
    }
}
