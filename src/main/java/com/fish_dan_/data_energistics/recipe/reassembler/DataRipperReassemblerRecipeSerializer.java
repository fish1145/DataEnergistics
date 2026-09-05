package com.fish_dan_.data_energistics.recipe.reassembler;

import appeng.api.stacks.GenericStack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Serializer for the concise, semantic data reassembler recipe format. */
public final class DataRipperReassemblerRecipeSerializer implements RecipeSerializer<DataRipperReassemblerRecipe> {

    private static final Codec<List<DataRipperReassemblerIngredient>> ITEM_INPUTS_CODEC = DataRipperReassemblerIngredient.CODEC.listOf().flatXmap(
            inputs -> inputs.size() > DataRipperReassemblerRecipe.ITEM_INPUT_SLOTS ? DataResult.error(() -> "Data reassembler supports at most " +
                    DataRipperReassemblerRecipe.ITEM_INPUT_SLOTS + " item inputs") : DataResult.success(inputs),
            DataResult::success);
    private static final Codec<List<GenericStack>> FLUID_INPUTS_CODEC = boundedFluidList(
            DataRipperReassemblerRecipe.FLUID_INPUT_SLOTS,
            "inputs");
    private static final Codec<List<DataReassemblerItemOutput>> ITEM_OUTPUTS_CODEC = DataReassemblerItemOutput.CODEC.codec().listOf().flatXmap(
            outputs -> outputs.size() > DataRipperReassemblerRecipe.ITEM_OUTPUT_SLOTS ? DataResult.error(() -> "Data reassembler supports at most " +
                    DataRipperReassemblerRecipe.ITEM_OUTPUT_SLOTS + " item outputs") : DataResult.success(outputs),
            DataResult::success);
    private static final Codec<List<GenericStack>> FLUID_OUTPUTS_CODEC = boundedFluidList(
            DataRipperReassemblerRecipe.FLUID_OUTPUT_SLOTS,
            "outputs");
    private static final Codec<GenericStack> RESOURCE_CODEC = DataReassemblerStackCodecs.RESOURCE.flatXmap(
            resource -> resource.amount() > DataRipperReassemblerRecipe.MAX_RESOURCE_AMOUNT ? DataResult.error(() -> "Data reassembler resource amount must not exceed " +
                    DataRipperReassemblerRecipe.MAX_RESOURCE_AMOUNT) : DataResult.success(resource),
            DataResult::success);
    private static final Codec<Integer> DURATION_CODEC = Codec.INT.flatXmap(
            duration -> duration <= 0 ? DataResult.error(() -> "Data reassembler duration must be greater than 0") : DataResult.success(duration),
            DataResult::success);

    private static final MapCodec<Inputs> RAW_INPUTS_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ITEM_INPUTS_CODEC.optionalFieldOf("items", List.of()).forGetter(Inputs::items),
            FLUID_INPUTS_CODEC.optionalFieldOf("fluids", List.of()).forGetter(Inputs::fluids),
            RESOURCE_CODEC.optionalFieldOf("resource").forGetter(Inputs::resource))
            .apply(instance, Inputs::new));
    private static final MapCodec<Inputs> INPUTS_CODEC = RAW_INPUTS_CODEC.flatXmap(
            inputs -> inputs.items().isEmpty() && inputs.fluids().isEmpty() && inputs.resource().isEmpty() ? DataResult.error(() -> "Data reassembler recipe must define at least one input") : DataResult.success(inputs),
            DataResult::success);
    private static final MapCodec<Outputs> RAW_OUTPUTS_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ITEM_OUTPUTS_CODEC.optionalFieldOf("items", List.of()).forGetter(Outputs::items),
            FLUID_OUTPUTS_CODEC.optionalFieldOf("fluids", List.of()).forGetter(Outputs::fluids),
            RESOURCE_CODEC.optionalFieldOf("resource").forGetter(Outputs::resource))
            .apply(instance, Outputs::new));
    private static final MapCodec<Outputs> OUTPUTS_CODEC = RAW_OUTPUTS_CODEC.flatXmap(
            outputs -> outputs.items().isEmpty() && outputs.fluids().isEmpty() && outputs.resource().isEmpty() ? DataResult.error(() -> "Data reassembler recipe must define at least one output") : DataResult.success(outputs),
            DataResult::success);

    private static final MapCodec<DataRipperReassemblerRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            INPUTS_CODEC.codec().fieldOf("inputs").forGetter(DataRipperReassemblerRecipeSerializer::inputs),
            OUTPUTS_CODEC.codec().fieldOf("outputs").forGetter(DataRipperReassemblerRecipeSerializer::outputs),
            DURATION_CODEC.optionalFieldOf("duration", DataRipperReassemblerRecipe.PROCESS_TICKS)
                    .forGetter(DataRipperReassemblerRecipe::getProcessTicks))
            .apply(instance, (inputs, outputs, duration) -> new DataRipperReassemblerRecipe(
                    inputs.items(),
                    inputs.fluids(),
                    outputs.items(),
                    outputs.fluids(),
                    duration,
                    inputs.resource().orElse(null),
                    outputs.resource().orElse(null))));

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<GenericStack>> OPTIONAL_RESOURCE_STREAM_CODEC = StreamCodec.of(DataRipperReassemblerRecipeSerializer::writeOptionalResource,
            DataRipperReassemblerRecipeSerializer::readOptionalResource);
    private static final StreamCodec<RegistryFriendlyByteBuf, List<GenericStack>> GENERIC_STACK_LIST_STREAM_CODEC = StreamCodec.of(DataRipperReassemblerRecipeSerializer::writeGenericStackList,
            DataRipperReassemblerRecipeSerializer::readGenericStackList);
    private static final StreamCodec<RegistryFriendlyByteBuf, DataRipperReassemblerRecipe> STREAM_CODEC = StreamCodec.of(DataRipperReassemblerRecipeSerializer::writeRecipe,
            DataRipperReassemblerRecipeSerializer::readRecipe);

    private static Codec<List<GenericStack>> boundedFluidList(int limit, String role) {
        return DataReassemblerStackCodecs.FLUID.listOf().flatXmap(
                fluids -> {
                    if (fluids.size() > limit) {
                        return DataResult.error(() -> "Data reassembler supports at most " + limit +
                                " fluid " + role);
                    }
                    for (GenericStack fluid : fluids) {
                        if (fluid.amount() > DataRipperReassemblerRecipe.MAX_FLUID_AMOUNT) {
                            return DataResult.error(() -> "Data reassembler fluid amount must not exceed " +
                                    DataRipperReassemblerRecipe.MAX_FLUID_AMOUNT);
                        }
                    }
                    return DataResult.success(fluids);
                },
                DataResult::success);
    }

    private static Inputs inputs(DataRipperReassemblerRecipe recipe) {
        return new Inputs(
                List.copyOf(recipe.getItemInputs()),
                recipe.getFluidInputs(),
                Optional.ofNullable(recipe.getKeyInput()));
    }

    private static Outputs outputs(DataRipperReassemblerRecipe recipe) {
        return new Outputs(
                recipe.getItemOutputDefinitions(),
                recipe.getFluidOutputs(),
                Optional.ofNullable(recipe.getKeyOutput()));
    }

    private static DataRipperReassemblerRecipe readRecipe(RegistryFriendlyByteBuf buffer) {
        List<DataRipperReassemblerIngredient> itemInputs = DataRipperReassemblerIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer);
        List<GenericStack> fluidInputs = GENERIC_STACK_LIST_STREAM_CODEC.decode(buffer);
        List<DataReassemblerItemOutput> itemOutputs = DataReassemblerItemOutput.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer);
        List<GenericStack> fluidOutputs = GENERIC_STACK_LIST_STREAM_CODEC.decode(buffer);
        int duration = ByteBufCodecs.VAR_INT.decode(buffer);
        GenericStack resourceInput = OPTIONAL_RESOURCE_STREAM_CODEC.decode(buffer).orElse(null);
        GenericStack resourceOutput = OPTIONAL_RESOURCE_STREAM_CODEC.decode(buffer).orElse(null);
        return new DataRipperReassemblerRecipe(
                itemInputs,
                fluidInputs,
                itemOutputs,
                fluidOutputs,
                duration,
                resourceInput,
                resourceOutput);
    }

    private static void writeRecipe(RegistryFriendlyByteBuf buffer, DataRipperReassemblerRecipe recipe) {
        DataRipperReassemblerIngredient.STREAM_CODEC.apply(ByteBufCodecs.list())
                .encode(buffer, List.copyOf(recipe.getItemInputs()));
        GENERIC_STACK_LIST_STREAM_CODEC.encode(buffer, recipe.getFluidInputs());
        DataReassemblerItemOutput.STREAM_CODEC.apply(ByteBufCodecs.list())
                .encode(buffer, recipe.getItemOutputDefinitions());
        GENERIC_STACK_LIST_STREAM_CODEC.encode(buffer, recipe.getFluidOutputs());
        ByteBufCodecs.VAR_INT.encode(buffer, recipe.getProcessTicks());
        OPTIONAL_RESOURCE_STREAM_CODEC.encode(buffer, Optional.ofNullable(recipe.getKeyInput()));
        OPTIONAL_RESOURCE_STREAM_CODEC.encode(buffer, Optional.ofNullable(recipe.getKeyOutput()));
    }

    private static Optional<GenericStack> readOptionalResource(RegistryFriendlyByteBuf buffer) {
        return Optional.ofNullable(GenericStack.readBuffer(buffer));
    }

    private static void writeOptionalResource(RegistryFriendlyByteBuf buffer, Optional<GenericStack> resource) {
        GenericStack.writeBuffer(resource.orElse(null), buffer);
    }

    private static List<GenericStack> readGenericStackList(RegistryFriendlyByteBuf buffer) {
        int size = ByteBufCodecs.VAR_INT.decode(buffer);
        List<GenericStack> stacks = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            stacks.add(Objects.requireNonNull(GenericStack.readBuffer(buffer), "Data reassembler fluid stack"));
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

    private record Inputs(List<DataRipperReassemblerIngredient> items, List<GenericStack> fluids,
                          Optional<GenericStack> resource) {}

    private record Outputs(List<DataReassemblerItemOutput> items, List<GenericStack> fluids,
                           Optional<GenericStack> resource) {}
}
