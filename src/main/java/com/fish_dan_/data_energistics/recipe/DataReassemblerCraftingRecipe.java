package com.fish_dan_.data_energistics.recipe;

import com.fish_dan_.data_energistics.registry.ModRecipes;
import com.fish_dan_.data_energistics.util.DataCaptureBallCraftingRemainderHelper;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

public final class DataReassemblerCraftingRecipe extends ShapedRecipe {

    private final ShapedRecipe wrapped;

    public DataReassemblerCraftingRecipe(ShapedRecipe wrapped) {
        super(wrapped.getGroup(), wrapped.category(), wrapped.pattern, ItemStack.EMPTY, wrapped.showNotification());
        this.wrapped = wrapped;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.wrapped.matches(input, level) && DataCaptureBallCraftingRemainderHelper.hasRequiredData(input);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return this.wrapped.assemble(input, registries);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.wrapped.getResultItem(registries);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remainders = this.wrapped.getRemainingItems(input);
        DataCaptureBallCraftingRemainderHelper.applyDataReassemblerRemainder(input, remainders);
        return remainders;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.DATA_REASSEMBLER_CRAFTING_SERIALIZER.get();
    }

    public ShapedRecipe wrapped() {
        return this.wrapped;
    }
}
