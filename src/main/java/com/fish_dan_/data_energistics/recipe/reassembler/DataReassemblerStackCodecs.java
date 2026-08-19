package com.fish_dan_.data_energistics.recipe.reassembler;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Author-facing codecs for fluid and custom-resource amounts used by data reassembler recipes. */
public final class DataReassemblerStackCodecs {

    private static final Codec<Long> POSITIVE_AMOUNT_CODEC = Codec.LONG.flatXmap(
            amount -> amount <= 0L ? DataResult.error(() -> "Data reassembler stack amount must be greater than 0") : DataResult.success(amount),
            DataResult::success);
    private static final Codec<FluidStackData> FLUID_DATA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(FluidStackData::fluid),
            POSITIVE_AMOUNT_CODEC.fieldOf("amount").forGetter(FluidStackData::amount))
            .apply(instance, FluidStackData::new));
    private static final MapCodec<AEKey> RESOURCE_KEY_CODEC = AEKeyType.CODEC
            .<AEKey>dispatchMap("key_type", AEKey::getType, AEKeyType::codec)
            .flatXmap(DataReassemblerStackCodecs::validateResourceKey, DataReassemblerStackCodecs::validateResourceKey);

    public static final Codec<GenericStack> FLUID = FLUID_DATA_CODEC.flatXmap(
            data -> data.fluid() == Fluids.EMPTY ? DataResult.error(() -> "Data reassembler fluid must not be empty") : DataResult.success(new GenericStack(AEFluidKey.of(data.fluid()), data.amount())),
            stack -> stack.what() instanceof AEFluidKey fluidKey ? DataResult.success(new FluidStackData(fluidKey.getFluid(), stack.amount())) : DataResult.error(() -> "Data reassembler fluid entry must contain a fluid key"));
    public static final Codec<GenericStack> RESOURCE = RecordCodecBuilder.create(instance -> instance.group(
            RESOURCE_KEY_CODEC.forGetter(GenericStack::what),
            POSITIVE_AMOUNT_CODEC.fieldOf("amount").forGetter(GenericStack::amount))
            .apply(instance, GenericStack::new));

    private DataReassemblerStackCodecs() {}

    private static DataResult<AEKey> validateResourceKey(AEKey key) {
        return key instanceof AEItemKey || key instanceof AEFluidKey ? DataResult.error(() -> "Data reassembler resource entries only accept custom AE key types") : DataResult.success(key);
    }

    private record FluidStackData(Fluid fluid, long amount) {}
}
