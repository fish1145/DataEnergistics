package com.fish_dan_.data_energistics.recipe.chargepress;

import com.fish_dan_.data_energistics.registry.DERecipes;

import appeng.api.stacks.GenericStack;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A normalized crystal-growth operation with one to three item inputs and a fixed process fluid and module. */
public final class DataChargePressRecipe implements Recipe<DataChargePressRecipeInput> {

    public static final int MIN_ITEM_INPUT_COUNT = 1;
    public static final int MAX_ITEM_INPUT_COUNT = 3;
    public static final int MAX_FLUID_AMOUNT = 51_200;

    private final List<DataChargePressIngredient> inputs;
    private final int fluidAmount;
    private final ItemStack result;

    public DataChargePressRecipe(List<DataChargePressIngredient> inputs, int fluidAmount, ItemStack result) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(result, "result");
        if (inputs.isEmpty() || inputs.size() > MAX_ITEM_INPUT_COUNT) {
            throw new IllegalArgumentException(
                    "Data charge press recipes require between " + MIN_ITEM_INPUT_COUNT + " and " +
                            MAX_ITEM_INPUT_COUNT + " inputs: " + inputs.size());
        }
        inputs.forEach(input -> Objects.requireNonNull(input, "input"));
        if (fluidAmount <= 0 || fluidAmount > MAX_FLUID_AMOUNT) {
            throw new IllegalArgumentException(
                    "Data charge press fluid amount must be between 1 and " + MAX_FLUID_AMOUNT + ": " + fluidAmount);
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Data charge press result must not be empty");
        }
        this.inputs = List.copyOf(inputs);
        this.fluidAmount = fluidAmount;
        this.result = result.copy();
    }

    @Override
    public boolean matches(DataChargePressRecipeInput input, Level level) {
        return getModule().test(input.module()) && matchesMachineInputs(input.items(), input.fluid());
    }

    @Override
    public ItemStack assemble(DataChargePressRecipeInput input, HolderLookup.Provider registries) {
        return this.getResultItem(registries).copy();
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

    public GenericStack getFluidInput() {
        return DataChargePressRecipeSupport.getFluidInput(this.fluidAmount);
    }

    public int getFluidAmount() {
        return this.fluidAmount;
    }

    public Ingredient getModule() {
        return DataChargePressRecipeSupport.CRYSTAL_GROWTH_MODULES;
    }

    public ItemStack getResult() {
        return this.result.copy();
    }

    /** Finds distinct input slots and their required consumption counts in recipe order. */
    public List<InputSlot> findMatchingInputSlots(List<ItemStack> inputs) {
        List<InputSlot> matches = new ArrayList<>(this.inputs.size());
        if (findMatchingInputSlots(inputs, 0, new boolean[inputs.size()], matches)) {
            return List.copyOf(matches);
        }
        return List.of();
    }

    /** Matches the consumable inputs after a machine has already selected its crystal-growth mode. */
    public boolean matchesMachineInputs(List<ItemStack> items, FluidStack fluid) {
        return matchesFluid(fluid) && !findMatchingInputSlots(items).isEmpty();
    }

    private boolean findMatchingInputSlots(List<ItemStack> inputs, int ingredientIndex, boolean[] usedSlots,
                                           List<InputSlot> matches) {
        if (ingredientIndex >= this.inputs.size()) {
            return true;
        }

        DataChargePressIngredient ingredient = this.inputs.get(ingredientIndex);
        for (int slot = 0; slot < inputs.size(); slot++) {
            ItemStack stack = inputs.get(slot);
            if (usedSlots[slot] || stack.getCount() < ingredient.count() || !ingredient.ingredient().test(stack)) {
                continue;
            }

            usedSlots[slot] = true;
            matches.add(new InputSlot(slot, ingredient.count()));
            if (findMatchingInputSlots(inputs, ingredientIndex + 1, usedSlots, matches)) {
                return true;
            }
            matches.removeLast();
            usedSlots[slot] = false;
        }
        return false;
    }

    private boolean matchesFluid(FluidStack fluid) {
        return DataChargePressRecipeSupport.matchesFluid(fluid, this.fluidAmount);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DERecipes.DATA_CHARGE_PRESS_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return DERecipes.DATA_CHARGE_PRESS_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    /** One physical input slot selected for this operation and the number of items to consume from it. */
    public record InputSlot(int slot, int count) {}
}
