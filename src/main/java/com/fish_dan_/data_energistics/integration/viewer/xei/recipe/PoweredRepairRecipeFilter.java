package com.fish_dan_.data_energistics.integration.viewer.xei.recipe;

import com.fish_dan_.data_energistics.registry.DEItems;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import mezz.jei.api.recipe.vanilla.IJeiAnvilRecipe;

import java.util.List;
import java.util.Set;

public final class PoweredRepairRecipeFilter {

    private static final Set<Item> HIDDEN_REPAIR_ITEMS = Set.of(
            DEItems.DATA_CRYSTAL_SWORD.get(),
            DEItems.DATA_CRYSTAL_AXE.get(),
            DEItems.DATA_CRYSTAL_PICKAXE.get(),
            DEItems.DATA_CRYSTAL_HOE.get(),
            DEItems.DATA_CRYSTAL_SHOVEL.get(),
            DEItems.DATA_CRYSTAL_CUTTING_KNIFE.get(),
            DEItems.DATA_LIGHT_SABER.get(),
            DEItems.DATA_SANCTIFIER.get());

    private PoweredRepairRecipeFilter() {}

    public static boolean shouldHideEmiRepairRecipe(EmiRecipe recipe) {
        if (recipe == null) {
            return false;
        }

        if (recipe.getCategory() == VanillaEmiRecipeCategories.ANVIL_REPAIRING) {
            return isTwoItemSelfRepairAnvil(recipe.getInputs(), recipe.getOutputs());
        }

        if (recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING) {
            return isTwoItemSelfRepairCraft(recipe.getInputs(), recipe.getOutputs());
        }

        return false;
    }

    public static boolean shouldHideJeiRepairRecipe(IJeiAnvilRecipe recipe) {
        if (recipe == null) {
            return false;
        }

        Item leftItem = getSingleTrackedItemFromStacks(recipe.getLeftInputs());
        Item rightItem = getSingleTrackedItemFromStacks(recipe.getRightInputs());
        return rightItem != null && leftItem == rightItem;
    }

    public static boolean shouldHideJeiCraftingRepairRecipe(RecipeHolder<CraftingRecipe> recipeHolder) {
        if (recipeHolder == null) {
            return false;
        }

        CraftingRecipe recipe = recipeHolder.value();
        Item ingredientItem = null;
        int ingredientCount = 0;
        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }

            Item item = getSingleTrackedItemFromStacks(List.of(ingredient.getItems()));
            if (item == null) {
                return false;
            }
            if (ingredientItem == null) {
                ingredientItem = item;
            } else if (ingredientItem != item) {
                return false;
            }
            ingredientCount++;
        }

        if (ingredientCount != 2) {
            return false;
        }

        Item outputItem = getTrackedItem(recipe.getResultItem(null));
        return outputItem == ingredientItem;
    }

    private static boolean isTwoItemSelfRepairAnvil(List<? extends EmiIngredient> inputs, List<EmiStack> outputs) {
        if (inputs.size() < 2) {
            return false;
        }

        Item left = getSingleTrackedItemFromEmiStacks(inputs.get(0).getEmiStacks());
        Item right = getSingleTrackedItemFromEmiStacks(inputs.get(1).getEmiStacks());
        Item output = getSingleTrackedItemFromEmiStacks(outputs);
        return left != null && left == right && left == output;
    }

    private static boolean isTwoItemSelfRepairCraft(List<? extends EmiIngredient> inputs, List<EmiStack> outputs) {
        if (inputs.size() != 2) {
            return false;
        }

        Item first = getSingleTrackedItemFromEmiStacks(inputs.get(0).getEmiStacks());
        Item second = getSingleTrackedItemFromEmiStacks(inputs.get(1).getEmiStacks());
        Item output = getSingleTrackedItemFromEmiStacks(outputs);
        return first != null && first == second && first == output;
    }

    private static Item getSingleTrackedItemFromEmiStacks(List<EmiStack> stacks) {
        for (EmiStack stack : stacks) {
            Item item = getTrackedItem(stack.getItemStack());
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    private static Item getSingleTrackedItemFromStacks(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            Item item = getTrackedItem(stack);
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    private static Item getTrackedItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        Item item = stack.getItem();
        return HIDDEN_REPAIR_ITEMS.contains(item) ? item : null;
    }
}
