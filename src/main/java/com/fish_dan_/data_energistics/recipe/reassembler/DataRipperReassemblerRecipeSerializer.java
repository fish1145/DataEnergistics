package com.fish_dan_.data_energistics.recipe.reassembler;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DataRipperReassemblerRecipeSerializer implements RecipeSerializer<DataRipperReassemblerRecipe> {

    private static final Codec<List<DataRipperReassemblerIngredient>> INPUTS_CODEC = DataRipperReassemblerIngredient.CODEC.codec().listOf();
    private static final Codec<List<ItemStack>> OUTPUTS_CODEC = ItemStack.CODEC.listOf();
    private static final Codec<Integer> TICKS_CODEC = Codec.INT.flatXmap(
            ticks -> ticks <= 0 ? DataResult.error(() -> "Data rippper reassembler recipe process_ticks must be greater than 0") : DataResult.success(ticks),
            DataResult::success);
    private static final Codec<GenericStack> KEY_INPUT_CODEC = GenericStack.CODEC.flatXmap(
            stack -> stack.amount() <= 0 ? DataResult.error(() -> "Data rippper reassembler recipe key_input amount must be greater than 0") : DataResult.success(stack),
            DataResult::success);
    private static final Codec<GenericStack> KEY_OUTPUT_CODEC = GenericStack.CODEC.flatXmap(
            stack -> stack.amount() <= 0 ? DataResult.error(() -> "Data rippper reassembler recipe key_output amount must be greater than 0") : DataResult.success(stack),
            DataResult::success);
    private static final Codec<GenericStack> FLUID_STACK_CODEC = GenericStack.CODEC.flatXmap(
            stack -> stack.amount() <= 0 || !(stack.what() instanceof AEFluidKey) ? DataResult.error(() -> "Data rippper reassembler fluid stack must be a positive fluid stack") : DataResult.success(stack),
            DataResult::success);
    private static final Codec<List<GenericStack>> FLUID_INPUTS_CODEC = FLUID_STACK_CODEC.listOf().flatXmap(
            fluids -> fluids.size() > DataRipperReassemblerRecipe.FLUID_INPUT_SLOTS ? DataResult.error(() -> "Data rippper reassembler recipe supports at most " + DataRipperReassemblerRecipe.FLUID_INPUT_SLOTS + " fluid inputs") : DataResult.success(fluids),
            DataResult::success);
    private static final Codec<List<GenericStack>> FLUID_OUTPUTS_CODEC = FLUID_STACK_CODEC.listOf().flatXmap(
            fluids -> fluids.size() > DataRipperReassemblerRecipe.FLUID_OUTPUT_SLOTS ? DataResult.error(() -> "Data rippper reassembler recipe supports at most " + DataRipperReassemblerRecipe.FLUID_OUTPUT_SLOTS + " fluid outputs") : DataResult.success(fluids),
            DataResult::success);
    private static final StreamCodec<RegistryFriendlyByteBuf, GenericStack> OPTIONAL_KEY_INPUT_STREAM_CODEC = StreamCodec.of(DataRipperReassemblerRecipeSerializer::writeOptionalKeyInput,
            DataRipperReassemblerRecipeSerializer::readOptionalKeyInput);
    private static final StreamCodec<RegistryFriendlyByteBuf, List<GenericStack>> GENERIC_STACK_LIST_STREAM_CODEC = StreamCodec.of(DataRipperReassemblerRecipeSerializer::writeGenericStackList,
            DataRipperReassemblerRecipeSerializer::readGenericStackList);

    private static final MapCodec<DataRipperReassemblerRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            INPUTS_CODEC.optionalFieldOf("item_inputs", List.of()).forGetter(DataRipperReassemblerRecipe::getItemInputs),
            FLUID_INPUTS_CODEC.optionalFieldOf("fluid_inputs", List.of()).forGetter(DataRipperReassemblerRecipe::getFluidInputs),
            OUTPUTS_CODEC.optionalFieldOf("item_outputs", List.of()).forGetter(DataRipperReassemblerRecipe::getItemOutputs),
            FLUID_OUTPUTS_CODEC.optionalFieldOf("fluid_outputs", List.of()).forGetter(DataRipperReassemblerRecipe::getFluidOutputs),
            TICKS_CODEC.optionalFieldOf("process_ticks", DataRipperReassemblerRecipe.PROCESS_TICKS)
                    .forGetter(DataRipperReassemblerRecipe::getProcessTicks),
            KEY_INPUT_CODEC.optionalFieldOf("key_input").forGetter(
                    (DataRipperReassemblerRecipe recipe) -> Optional.ofNullable(recipe.getKeyInput())),
            KEY_OUTPUT_CODEC.optionalFieldOf("key_output").forGetter(
                    (DataRipperReassemblerRecipe recipe) -> Optional.ofNullable(recipe.getKeyOutput())),
            Codec.BOOL.optionalFieldOf("assign_quantum_frequency", false).forGetter(DataRipperReassemblerRecipe::assignsQuantumFrequency))
            .apply(instance, (itemInputs, fluidInputs, itemOutputs, fluidOutputs, processTicks, keyInput, keyOutput, assignQuantumFrequency) -> new DataRipperReassemblerRecipe(
                    itemInputs,
                    fluidInputs,
                    itemOutputs,
                    fluidOutputs,
                    processTicks,
                    keyInput.orElse(null),
                    keyOutput.orElse(null),
                    assignQuantumFrequency)));

    private static final StreamCodec<RegistryFriendlyByteBuf, DataRipperReassemblerRecipe> STREAM_CODEC = StreamCodec.of(DataRipperReassemblerRecipeSerializer::writeRecipe, DataRipperReassemblerRecipeSerializer::readRecipe);

    private static GenericStack readOptionalKeyInput(RegistryFriendlyByteBuf buffer) {
        return GenericStack.readBuffer(buffer);
    }

    private static void writeOptionalKeyInput(RegistryFriendlyByteBuf buffer, GenericStack keyInput) {
        GenericStack.writeBuffer(keyInput, buffer);
    }

    private static DataRipperReassemblerRecipe readRecipe(RegistryFriendlyByteBuf buffer) {
        List<DataRipperReassemblerIngredient> itemInputs = DataRipperReassemblerIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer);
        List<GenericStack> fluidInputs = GENERIC_STACK_LIST_STREAM_CODEC.decode(buffer);
        List<ItemStack> itemOutputs = ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer);
        List<GenericStack> fluidOutputs = GENERIC_STACK_LIST_STREAM_CODEC.decode(buffer);
        int processTicks = ByteBufCodecs.VAR_INT.decode(buffer);
        GenericStack keyInput = OPTIONAL_KEY_INPUT_STREAM_CODEC.decode(buffer);
        GenericStack keyOutput = OPTIONAL_KEY_INPUT_STREAM_CODEC.decode(buffer);
        boolean assignQuantumFrequency = ByteBufCodecs.BOOL.decode(buffer);
        return new DataRipperReassemblerRecipe(
                itemInputs,
                fluidInputs,
                itemOutputs,
                fluidOutputs,
                processTicks,
                keyInput,
                keyOutput,
                assignQuantumFrequency);
    }

    private static void writeRecipe(RegistryFriendlyByteBuf buffer, DataRipperReassemblerRecipe recipe) {
        DataRipperReassemblerIngredient.STREAM_CODEC.apply(ByteBufCodecs.list())
                .encode(buffer, List.copyOf(recipe.getItemInputs()));
        GENERIC_STACK_LIST_STREAM_CODEC.encode(buffer, recipe.getFluidInputs());
        ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, List.copyOf(recipe.getItemOutputs()));
        GENERIC_STACK_LIST_STREAM_CODEC.encode(buffer, recipe.getFluidOutputs());
        ByteBufCodecs.VAR_INT.encode(buffer, recipe.getProcessTicks());
        OPTIONAL_KEY_INPUT_STREAM_CODEC.encode(buffer, recipe.getKeyInput());
        OPTIONAL_KEY_INPUT_STREAM_CODEC.encode(buffer, recipe.getKeyOutput());
        ByteBufCodecs.BOOL.encode(buffer, recipe.assignsQuantumFrequency());
    }

    private static List<GenericStack> readGenericStackList(RegistryFriendlyByteBuf buffer) {
        int size = ByteBufCodecs.VAR_INT.decode(buffer);
        List<GenericStack> stacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stacks.add(GenericStack.readBuffer(buffer));
        }
        return List.copyOf(stacks);
    }

    private static void writeGenericStackList(RegistryFriendlyByteBuf buffer, List<GenericStack> stacks) {
        ByteBufCodecs.VAR_INT.encode(buffer, stacks.size());
        for (GenericStack stack : stacks) {
            GenericStack.writeBuffer(stack, buffer);
        }
    }

    @Override
    public MapCodec<DataRipperReassemblerRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, DataRipperReassemblerRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
