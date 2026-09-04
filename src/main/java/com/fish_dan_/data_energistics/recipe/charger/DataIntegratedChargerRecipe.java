package com.fish_dan_.data_energistics.recipe.charger;

import com.fish_dan_.data_energistics.recipe.chargepress.DataChargePressIngredient;
import com.fish_dan_.data_energistics.registry.DERecipes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A multi-input recipe executed by the data integrated charger's inscribing mode. */
public final class DataIntegratedChargerRecipe implements Recipe<DataIntegratedChargerRecipeInput> {

    public static final int MIN_ITEM_INPUT_COUNT = 1;
    public static final int MAX_ITEM_INPUT_COUNT = 3;

    private final List<DataChargePressIngredient> inputs;
    private final ItemStack result;

    public DataIntegratedChargerRecipe(List<DataChargePressIngredient> inputs, ItemStack result) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(result, "result");
        if (inputs.isEmpty() || inputs.size() > MAX_ITEM_INPUT_COUNT) {
            throw new IllegalArgumentException(
                    "Data integrated charger recipes require between " + MIN_ITEM_INPUT_COUNT + " and " +
                            MAX_ITEM_INPUT_COUNT + " inputs: " + inputs.size());
        }
        inputs.forEach(input -> Objects.requireNonNull(input, "input"));
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Data integrated charger result must not be empty");
        }
        this.inputs = List.copyOf(inputs);
        this.result = result.copy();
    }

    @Override
    public boolean matches(DataIntegratedChargerRecipeInput input, Level level) {
        return !findMatchingInputSlots(input.items()).isEmpty();
    }

    @Override
    public ItemStack assemble(DataIntegratedChargerRecipeInput input, HolderLookup.Provider registries) {
        return getResultItem(registries).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.copyOf(this.inputs.stream().map(DataChargePressIngredient::ingredient).toList());
    }

    public List<DataChargePressIngredient> getInputs() {
        return this.inputs;
    }

    public ItemStack getResult() {
        return this.result.copy();
    }

    /** Finds distinct input slots and their required consumption counts in recipe order. */
    public List<InputSlot> findMatchingInputSlots(List<ItemStack> availableInputs) {
        List<InputSlot> matches = new ArrayList<>(this.inputs.size());
        if (findMatchingInputSlots(availableInputs, 0, new boolean[availableInputs.size()], matches)) {
            return List.copyOf(matches);
        }
        return List.of();
    }

    private boolean findMatchingInputSlots(List<ItemStack> availableInputs, int ingredientIndex,
                                           boolean[] usedSlots, List<InputSlot> matches) {
        if (ingredientIndex >= this.inputs.size()) {
            return true;
        }

        DataChargePressIngredient ingredient = this.inputs.get(ingredientIndex);
        for (int slot = 0; slot < availableInputs.size(); slot++) {
            ItemStack stack = availableInputs.get(slot);
            if (usedSlots[slot] || stack.getCount() < ingredient.count() || !ingredient.ingredient().test(stack)) {
                continue;
            }

            usedSlots[slot] = true;
            matches.add(new InputSlot(slot, ingredient.count()));
            if (findMatchingInputSlots(availableInputs, ingredientIndex + 1, usedSlots, matches)) {
                return true;
            }
            matches.removeLast();
            usedSlots[slot] = false;
        }
        return false;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DERecipes.DATA_INTEGRATED_CHARGER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return DERecipes.DATA_INTEGRATED_CHARGER_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    /** One physical input slot selected for this operation and the number of items to consume from it. */
    public record InputSlot(int slot, int count) {}
}
