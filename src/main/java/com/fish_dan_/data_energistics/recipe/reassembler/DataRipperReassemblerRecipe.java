package com.fish_dan_.data_energistics.recipe.reassembler;

import com.fish_dan_.data_energistics.registry.ModRecipes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.core.definitions.AEItems;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DataRipperReassemblerRecipe implements Recipe<DataRipperReassemblerRecipeInput> {

    public static final int PROCESS_TICKS = 200;
    public static final int ITEM_INPUT_SLOTS = 9;
    public static final int KEY_INPUT_SLOTS = 1;
    public static final int FLUID_INPUT_SLOTS = 2;
    public static final int ITEM_OUTPUT_SLOTS = 3;
    public static final int KEY_OUTPUT_SLOTS = 1;
    public static final int FLUID_OUTPUT_SLOTS = 2;
    public static final int KEY_INPUT_SLOT_INDEX = ITEM_INPUT_SLOTS;
    private final NonNullList<DataRipperReassemblerIngredient> itemInputs;
    private final List<GenericStack> fluidInputs;
    private final NonNullList<ItemStack> itemOutputs;
    private final List<GenericStack> fluidOutputs;
    private final int processTicks;
    @Nullable
    private final GenericStack keyInput;
    @Nullable
    private final GenericStack keyOutput;
    private final boolean assignQuantumFrequency;

    public DataRipperReassemblerRecipe(List<DataRipperReassemblerIngredient> itemInputs,
                                       List<GenericStack> fluidInputs,
                                       List<ItemStack> itemOutputs,
                                       List<GenericStack> fluidOutputs,
                                       int processTicks,
                                       @Nullable GenericStack keyInput,
                                       @Nullable GenericStack keyOutput) {
        this(itemInputs, fluidInputs, itemOutputs, fluidOutputs, processTicks, keyInput, keyOutput, false);
    }

    public DataRipperReassemblerRecipe(List<DataRipperReassemblerIngredient> itemInputs,
                                       List<GenericStack> fluidInputs,
                                       List<ItemStack> itemOutputs,
                                       List<GenericStack> fluidOutputs,
                                       int processTicks,
                                       @Nullable GenericStack keyInput,
                                       @Nullable GenericStack keyOutput,
                                       boolean assignQuantumFrequency) {
        this.itemInputs = NonNullList.copyOf(itemInputs);
        this.fluidInputs = List.copyOf(fluidInputs);
        this.itemOutputs = NonNullList.copyOf(itemOutputs);
        this.fluidOutputs = List.copyOf(fluidOutputs);
        this.processTicks = processTicks;
        this.keyInput = keyInput != null && keyInput.amount() > 0 ? keyInput : null;
        this.keyOutput = keyOutput != null && keyOutput.amount() > 0 ? keyOutput : null;
        this.assignQuantumFrequency = assignQuantumFrequency;
    }

    @Override
    public boolean matches(DataRipperReassemblerRecipeInput input, Level level) {
        if (!matchesKeyInput(input.keyInput())) {
            return false;
        }
        if (!matchesFluidInputs(input.fluidInputs())) {
            return false;
        }

        List<ItemStack> remaining = new ArrayList<>(input.items().size());
        for (ItemStack stack : input.items()) {
            remaining.add(stack.copy());
        }

        for (DataRipperReassemblerIngredient countedIngredient : getItemInputsForMatching()) {
            int required = countedIngredient.count();
            for (ItemStack stack : remaining) {
                if (required <= 0) {
                    break;
                }
                if (!countedIngredient.ingredient().test(stack)) {
                    continue;
                }

                int consumed = Math.min(required, stack.getCount());
                required -= consumed;
                stack.shrink(consumed);
            }

            if (required > 0) {
                return false;
            }
        }

        return true;
    }

    private List<DataRipperReassemblerIngredient> getItemInputsForMatching() {
        List<DataRipperReassemblerIngredient> matchingOrder = new ArrayList<>(this.itemInputs);
        int segmentStart = 0;
        for (int index = 0; index <= matchingOrder.size(); index++) {
            if (index == matchingOrder.size() || getItemIngredientMatchPriority(matchingOrder.get(index).ingredient()) == ItemIngredientMatchPriority.UNKNOWN) {
                matchingOrder.subList(segmentStart, index).sort(DataRipperReassemblerRecipe::compareKnownItemIngredients);
                segmentStart = index + 1;
            }
        }
        return matchingOrder;
    }

    private static int compareKnownItemIngredients(DataRipperReassemblerIngredient left,
                                                   DataRipperReassemblerIngredient right) {
        ItemIngredientMatchPriority leftPriority = getItemIngredientMatchPriority(left.ingredient());
        ItemIngredientMatchPriority rightPriority = getItemIngredientMatchPriority(right.ingredient());
        int priorityComparison = leftPriority.compareTo(rightPriority);
        if (priorityComparison != 0) {
            return priorityComparison;
        }
        if (leftPriority == ItemIngredientMatchPriority.EXPLICIT) {
            return Integer.compare(left.ingredient().getItems().length, right.ingredient().getItems().length);
        }
        return 0;
    }

    private static ItemIngredientMatchPriority getItemIngredientMatchPriority(Ingredient ingredient) {
        if (ingredient.isCustom()) {
            return ItemIngredientMatchPriority.UNKNOWN;
        }
        Ingredient.Value[] values = ingredient.getValues();
        if (values.length == 0) {
            return ItemIngredientMatchPriority.UNKNOWN;
        }
        if (values.length == 1 && values[0] instanceof Ingredient.ItemValue) {
            return ItemIngredientMatchPriority.EXACT;
        }
        boolean containsTag = false;
        for (Ingredient.Value value : values) {
            if (value instanceof Ingredient.TagValue) {
                containsTag = true;
            } else if (!(value instanceof Ingredient.ItemValue)) {
                return ItemIngredientMatchPriority.UNKNOWN;
            }
        }
        return containsTag ? ItemIngredientMatchPriority.TAG : ItemIngredientMatchPriority.EXPLICIT;
    }

    private enum ItemIngredientMatchPriority {
        EXACT,
        EXPLICIT,
        TAG,
        UNKNOWN
    }

    @Override
    public ItemStack assemble(DataRipperReassemblerRecipeInput input, HolderLookup.Provider registries) {
        NonNullList<ItemStack> outputs = this.getCraftedItemOutputs();
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.getFirst();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.itemOutputs.isEmpty() ? ItemStack.EMPTY : this.itemOutputs.getFirst();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> expanded = NonNullList.create();
        for (DataRipperReassemblerIngredient countedIngredient : this.itemInputs) {
            for (int i = 0; i < countedIngredient.count(); i++) {
                expanded.add(countedIngredient.ingredient());
            }
        }
        return expanded;
    }

    public NonNullList<DataRipperReassemblerIngredient> getItemInputs() {
        return this.itemInputs;
    }

    public List<GenericStack> getFluidInputs() {
        return this.fluidInputs;
    }

    @Nullable
    public Map<AEFluidKey, Long> getMergedFluidInputAmounts() {
        Map<AEFluidKey, Long> merged = new LinkedHashMap<>();
        for (GenericStack fluidInput : this.fluidInputs) {
            if (!(fluidInput.what() instanceof AEFluidKey fluidKey) || fluidInput.amount() <= 0) {
                return null;
            }
            long current = merged.getOrDefault(fluidKey, 0L);
            if (fluidInput.amount() > Long.MAX_VALUE - current) {
                return null;
            }
            merged.put(fluidKey, current + fluidInput.amount());
        }
        return merged;
    }

    public NonNullList<ItemStack> getItemOutputs() {
        return this.itemOutputs;
    }

    public NonNullList<ItemStack> getCraftedItemOutputs() {
        NonNullList<ItemStack> outputs = NonNullList.create();
        for (ItemStack output : this.itemOutputs) {
            outputs.add(output.copy());
        }

        if (!outputs.isEmpty()) {
            ItemStack result = outputs.getFirst();
            if (this.assignQuantumFrequency && AEItems.QUANTUM_ENTANGLED_SINGULARITY.is(result) && result.getCount() > 1) {
                QuantumBridgeBlockEntity.assignFrequency(result);
            }
        }
        return outputs;
    }

    public List<GenericStack> getFluidOutputs() {
        return this.fluidOutputs;
    }

    public int getProcessTicks() {
        return this.processTicks;
    }

    @Nullable
    public GenericStack getKeyInput() {
        return this.keyInput;
    }

    @Nullable
    public GenericStack getKeyOutput() {
        return this.keyOutput;
    }

    public boolean assignsQuantumFrequency() {
        return this.assignQuantumFrequency;
    }

    private boolean matchesKeyInput(@Nullable GenericStack inputKey) {
        if (this.keyInput == null) {
            return true;
        }
        if (inputKey == null) {
            return false;
        }
        return this.keyInput.what().equals(inputKey.what()) && inputKey.amount() >= this.keyInput.amount();
    }

    private boolean matchesFluidInputs(List<GenericStack> inputFluids) {
        Map<AEFluidKey, Long> required = getMergedFluidInputAmounts();
        if (required == null) {
            return false;
        }

        Map<AEFluidKey, Long> available = new HashMap<>();
        for (GenericStack fluid : inputFluids) {
            if (fluid == null || !(fluid.what() instanceof AEFluidKey) || fluid.amount() <= 0) {
                continue;
            }
            available.merge((AEFluidKey) fluid.what(), fluid.amount(), Long::sum);
        }

        for (Map.Entry<AEFluidKey, Long> requirement : required.entrySet()) {
            if (available.getOrDefault(requirement.getKey(), 0L) < requirement.getValue()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.DATA_RIPPER_REASSEMBLER_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.DATA_RIPPER_REASSEMBLER_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }
}
