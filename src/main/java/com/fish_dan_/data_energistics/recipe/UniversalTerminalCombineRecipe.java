package com.fish_dan_.data_energistics.recipe;

import com.fish_dan_.data_energistics.registry.DEItems;
import com.fish_dan_.data_energistics.registry.DERecipes;
import com.fish_dan_.data_energistics.util.UniversalTerminalData;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class UniversalTerminalCombineRecipe extends CustomRecipe {

    public UniversalTerminalCombineRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return !assemble(input, level.registryAccess()).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        List<ItemStack> nonEmptyStacks = getNonEmptyStacks(input);
        if (nonEmptyStacks.size() != 2) {
            return ItemStack.EMPTY;
        }

        ItemStack first = nonEmptyStacks.get(0);
        ItemStack second = nonEmptyStacks.get(1);

        boolean firstUniversal = UniversalTerminalData.isUniversalTerminal(first);
        boolean secondUniversal = UniversalTerminalData.isUniversalTerminal(second);

        if (firstUniversal && secondUniversal) {
            return ItemStack.EMPTY;
        }

        if (firstUniversal) {
            return UniversalTerminalData.isSupportedTerminal(second) ? UniversalTerminalData.upgradeTerminal(first, second, registries) : ItemStack.EMPTY;
        }

        if (secondUniversal) {
            return UniversalTerminalData.isSupportedTerminal(first) ? UniversalTerminalData.upgradeTerminal(second, first, registries) : ItemStack.EMPTY;
        }

        String firstName = UniversalTerminalData.getTerminalName(first);
        String secondName = UniversalTerminalData.getTerminalName(second);
        if (firstName == null || secondName == null || Objects.equals(firstName, secondName)) {
            return ItemStack.EMPTY;
        }

        return UniversalTerminalData.createCombinedTerminal(
                new ItemStack(DEItems.UNIVERSAL_TERMINAL.get()),
                registries,
                first,
                second);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        return NonNullList.withSize(input.size(), ItemStack.EMPTY);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DERecipes.UNIVERSAL_TERMINAL_COMBINE_SERIALIZER.get();
    }

    private static List<ItemStack> getNonEmptyStacks(CraftingInput input) {
        List<ItemStack> stacks = new ArrayList<>(2);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        return stacks;
    }
}
