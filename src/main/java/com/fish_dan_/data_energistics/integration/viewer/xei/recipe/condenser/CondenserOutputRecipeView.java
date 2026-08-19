package com.fish_dan_.data_energistics.integration.viewer.xei.recipe.condenser;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.recipe.condenser.CondenserOutputRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.Arrays;
import java.util.List;

/** Immutable viewer projection of one authoritative matter condenser output recipe. */
public final class CondenserOutputRecipeView {

    private final ResourceLocation id;
    private final List<ItemStack> storageCandidates;
    private final ItemStack result;
    private final long requiredPower;

    private CondenserOutputRecipeView(ResourceLocation id, List<ItemStack> storageCandidates, ItemStack result,
                                      long requiredPower) {
        this.id = id;
        this.storageCandidates = storageCandidates.stream().map(ItemStack::copy).toList();
        this.result = result.copy();
        this.requiredPower = requiredPower;
    }

    public static CondenserOutputRecipeView from(RecipeHolder<CondenserOutputRecipe> holder) {
        CondenserOutputRecipe recipe = holder.value();
        List<ItemStack> storageCandidates = Arrays.stream(recipe.getStorageIngredient().getItems())
                .filter(recipe::acceptsStorage)
                .map(ItemStack::copy)
                .toList();
        if (storageCandidates.isEmpty()) {
            String message = "Matter condenser recipe " + holder.id() + " has no valid storage component candidates";
            Data_Energistics.LOGGER.error(message);
            throw new IllegalArgumentException(message);
        }
        return new CondenserOutputRecipeView(holder.id(), storageCandidates, recipe.getResult(),
                recipe.getRequiredPower());
    }

    public ResourceLocation id() {
        return this.id;
    }

    public List<ItemStack> storageCandidates() {
        return this.storageCandidates.stream().map(ItemStack::copy).toList();
    }

    public ItemStack result() {
        return this.result.copy();
    }

    public long requiredPower() {
        return this.requiredPower;
    }
}
