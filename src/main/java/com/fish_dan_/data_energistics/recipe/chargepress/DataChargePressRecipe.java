package com.fish_dan_.data_energistics.recipe.chargepress;

import com.fish_dan_.data_energistics.registry.DERecipes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;

import java.util.ArrayList;
import java.util.List;

/** A one-to-three-input, fluid-backed operation performed by the data integrated charger. */
public final class DataChargePressRecipe implements Recipe<DataChargePressRecipeInput> {

    public static final int MIN_ITEM_INPUT_COUNT = 1;
    public static final int MAX_ITEM_INPUT_COUNT = 3;

    private final List<DataChargePressIngredient> itemInputs;
    private final GenericStack fluidInput;
    private final Ingredient catalyst;
    private final ItemStack result;

    public DataChargePressRecipe(List<DataChargePressIngredient> itemInputs, GenericStack fluidInput, Ingredient catalyst,
                                 ItemStack result) {
        this.itemInputs = List.copyOf(itemInputs);
        this.fluidInput = fluidInput;
        this.catalyst = catalyst;
        this.result = result.copy();
    }

    @Override
    public boolean matches(DataChargePressRecipeInput input, Level level) {
        return this.catalyst.test(input.catalyst()) && matchesFluid(input.fluid()) &&
                !findMatchingInputSlots(input.items()).isEmpty();
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
        return NonNullList.copyOf(this.itemInputs.stream().map(DataChargePressIngredient::ingredient).toList());
    }

    public List<DataChargePressIngredient> getItemInputs() {
        return this.itemInputs;
    }

    public GenericStack getFluidInput() {
        return this.fluidInput;
    }

    public Ingredient getCatalyst() {
        return this.catalyst;
    }

    public ItemStack getResult() {
        return this.result.copy();
    }

    /** Finds distinct input slots and their required consumption counts in recipe order. */
    public List<InputSlot> findMatchingInputSlots(List<ItemStack> inputs) {
        if (this.itemInputs.size() < MIN_ITEM_INPUT_COUNT || this.itemInputs.size() > MAX_ITEM_INPUT_COUNT) {
            return List.of();
        }

        List<InputSlot> matches = new ArrayList<>(this.itemInputs.size());
        if (findMatchingInputSlots(inputs, 0, new boolean[inputs.size()], matches)) {
            return List.copyOf(matches);
        }
        return List.of();
    }

    private boolean findMatchingInputSlots(List<ItemStack> inputs, int ingredientIndex, boolean[] usedSlots,
                                           List<InputSlot> matches) {
        if (ingredientIndex >= this.itemInputs.size()) {
            return true;
        }

        DataChargePressIngredient ingredient = this.itemInputs.get(ingredientIndex);
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
        if (fluid.isEmpty() || !(this.fluidInput.what() instanceof AEFluidKey fluidKey)) {
            return false;
        }
        return fluid.getAmount() >= this.fluidInput.amount() && fluidKey.equals(AEFluidKey.of(fluid));
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
