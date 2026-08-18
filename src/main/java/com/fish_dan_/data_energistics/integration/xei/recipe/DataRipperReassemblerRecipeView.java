package com.fish_dan_.data_energistics.integration.xei.recipe;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerIngredient;
import com.fish_dan_.data_energistics.recipe.reassembler.DataRipperReassemblerRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Immutable, validated client view of a data reassembler recipe shared by recipe viewers.
 */
@Accessors(fluent = true)
public final class DataRipperReassemblerRecipeView {

    @Getter
    private final ResourceLocation id;
    @Getter
    private final List<DataRipperReassemblerIngredient> itemInputs;
    @Getter
    private final List<GenericStack> fluidInputs;
    private final List<ItemStack> itemOutputs;
    @Getter
    private final List<GenericStack> fluidOutputs;
    @Getter
    private final int processTicks;
    @Getter
    @Nullable
    private final GenericStack keyInput;
    @Getter
    @Nullable
    private final GenericStack keyOutput;

    private DataRipperReassemblerRecipeView(ResourceLocation id,
                                            List<DataRipperReassemblerIngredient> itemInputs,
                                            List<GenericStack> fluidInputs,
                                            List<ItemStack> itemOutputs,
                                            List<GenericStack> fluidOutputs,
                                            int processTicks,
                                            @Nullable GenericStack keyInput,
                                            @Nullable GenericStack keyOutput) {
        this.id = id;
        this.itemInputs = validateItemInputs(itemInputs);
        this.fluidInputs = validateFluidStacks(fluidInputs, DataRipperReassemblerRecipe.FLUID_INPUT_SLOTS, "fluid inputs");
        this.itemOutputs = validateItemOutputs(itemOutputs);
        this.fluidOutputs = validateFluidStacks(fluidOutputs, DataRipperReassemblerRecipe.FLUID_OUTPUT_SLOTS, "fluid outputs");
        if (processTicks <= 0) {
            throw validationError("Data reassembler process ticks must be positive: " + processTicks);
        }
        this.processTicks = processTicks;
        this.keyInput = validateKeyStack(keyInput, "key input");
        this.keyOutput = validateKeyStack(keyOutput, "key output");
    }

    /**
     * Creates the viewer contract while retaining the authoritative recipe identifier.
     */
    public static DataRipperReassemblerRecipeView from(RecipeHolder<DataRipperReassemblerRecipe> holder) {
        DataRipperReassemblerRecipe recipe = holder.value();
        return new DataRipperReassemblerRecipeView(
                holder.id(),
                recipe.getItemInputs(),
                recipe.getFluidInputs(),
                recipe.getCraftedItemOutputs(),
                recipe.getFluidOutputs(),
                recipe.getProcessTicks(),
                recipe.getKeyInput(),
                recipe.getKeyOutput());
    }

    public List<ItemStack> itemOutputs() {
        return copyItemStacks(this.itemOutputs);
    }

    private static List<DataRipperReassemblerIngredient> validateItemInputs(
                                                                            List<DataRipperReassemblerIngredient> inputs) {
        if (inputs.size() > DataRipperReassemblerRecipe.ITEM_INPUT_SLOTS) {
            throw validationError(
                    "Data reassembler item inputs exceed " + DataRipperReassemblerRecipe.ITEM_INPUT_SLOTS + ": " + inputs.size());
        }
        for (DataRipperReassemblerIngredient input : inputs) {
            if (input.count() <= 0) {
                throw validationError("Data reassembler item input amount must be positive: " + input.count());
            }
            if (input.ingredient().isEmpty()) {
                throw validationError("Data reassembler item input must resolve to at least one item");
            }
        }
        return List.copyOf(inputs);
    }

    private static List<ItemStack> validateItemOutputs(List<ItemStack> outputs) {
        if (outputs.size() > DataRipperReassemblerRecipe.ITEM_OUTPUT_SLOTS) {
            throw validationError(
                    "Data reassembler item outputs exceed " + DataRipperReassemblerRecipe.ITEM_OUTPUT_SLOTS + ": " + outputs.size());
        }
        for (ItemStack output : outputs) {
            if (output.isEmpty()) {
                throw validationError("Data reassembler item output amount must be positive");
            }
        }
        return copyItemStacks(outputs);
    }

    private static List<GenericStack> validateFluidStacks(List<GenericStack> stacks, int limit, String description) {
        if (stacks.size() > limit) {
            throw validationError("Data reassembler " + description + " exceed " + limit + ": " + stacks.size());
        }
        for (GenericStack stack : stacks) {
            validatePositiveStack(stack, description);
            if (!(stack.what() instanceof AEFluidKey)) {
                throw validationError("Data reassembler " + description + " only accept fluid keys: " + stack.what());
            }
        }
        return List.copyOf(stacks);
    }

    @Nullable
    private static GenericStack validateKeyStack(@Nullable GenericStack stack, String description) {
        if (stack == null) {
            return null;
        }
        validatePositiveStack(stack, description);
        if (stack.what() instanceof AEItemKey || stack.what() instanceof AEFluidKey) {
            throw validationError(
                    "Data reassembler " + description + " only accepts custom keys: " + stack.what());
        }
        return stack;
    }

    private static void validatePositiveStack(GenericStack stack, String description) {
        if (stack.amount() <= 0L) {
            throw validationError(
                    "Data reassembler " + description + " amount must be positive: " + stack.amount());
        }
    }

    private static IllegalArgumentException validationError(String message) {
        Data_Energistics.LOGGER.error(message);
        return new IllegalArgumentException(message);
    }

    private static List<ItemStack> copyItemStacks(List<ItemStack> stacks) {
        return stacks.stream().map(ItemStack::copy).toList();
    }
}
