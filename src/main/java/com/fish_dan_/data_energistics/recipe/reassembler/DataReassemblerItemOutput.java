package com.fish_dan_.data_energistics.recipe.reassembler;

import com.fish_dan_.data_energistics.Data_Energistics;

import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.core.definitions.AEItems;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** One exact item output with an optional, registered post-assembly modifier. */
public final class DataReassemblerItemOutput {

    public static final ResourceLocation ASSIGN_QUANTUM_FREQUENCY = Data_Energistics.id("assign_quantum_frequency");

    public static final MapCodec<DataReassemblerItemOutput> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(output -> output.stack.getItemHolder()),
            ExtraCodecs.intRange(1, 99).optionalFieldOf("count", 1).forGetter(output -> output.stack.getCount()),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY)
                    .forGetter(output -> output.stack.getComponentsPatch()),
            ResourceLocation.CODEC.optionalFieldOf("modifier").forGetter(
                    output -> Optional.ofNullable(output.modifier)))
            .apply(instance, (item, count, components, modifier) -> new DataReassemblerItemOutput(
                    new ItemStack(item, count, components),
                    modifier.orElse(null))));

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<ResourceLocation>> OPTIONAL_MODIFIER_STREAM_CODEC = StreamCodec.of(DataReassemblerItemOutput::writeOptionalModifier, DataReassemblerItemOutput::readOptionalModifier);
    public static final StreamCodec<RegistryFriendlyByteBuf, DataReassemblerItemOutput> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC,
            DataReassemblerItemOutput::stack,
            OPTIONAL_MODIFIER_STREAM_CODEC,
            output -> Optional.ofNullable(output.modifier),
            (stack, modifier) -> new DataReassemblerItemOutput(stack, modifier.orElse(null)));

    private final ItemStack stack;
    @Nullable
    private final ResourceLocation modifier;

    public DataReassemblerItemOutput(ItemStack stack, @Nullable ResourceLocation modifier) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Data reassembler item output must not be empty");
        }
        if (modifier != null && !ASSIGN_QUANTUM_FREQUENCY.equals(modifier)) {
            throw new IllegalArgumentException("Unknown data reassembler output modifier: " + modifier);
        }
        if (ASSIGN_QUANTUM_FREQUENCY.equals(modifier) &&
                (!AEItems.QUANTUM_ENTANGLED_SINGULARITY.is(stack) || stack.getCount() < 2)) {
            throw new IllegalArgumentException(
                    "assign_quantum_frequency requires at least two quantum entangled singularities");
        }
        this.stack = stack.copy();
        this.modifier = modifier;
    }

    private static Optional<ResourceLocation> readOptionalModifier(RegistryFriendlyByteBuf buffer) {
        return ByteBufCodecs.BOOL.decode(buffer) ? Optional.of(ResourceLocation.STREAM_CODEC.decode(buffer)) : Optional.empty();
    }

    private static void writeOptionalModifier(RegistryFriendlyByteBuf buffer, Optional<ResourceLocation> modifier) {
        ByteBufCodecs.BOOL.encode(buffer, modifier.isPresent());
        modifier.ifPresent(value -> ResourceLocation.STREAM_CODEC.encode(buffer, value));
    }

    public ItemStack stack() {
        return this.stack.copy();
    }

    public ItemStack createStack() {
        ItemStack result = this.stack.copy();
        if (ASSIGN_QUANTUM_FREQUENCY.equals(this.modifier)) {
            QuantumBridgeBlockEntity.assignFrequency(result);
        }
        return result;
    }

    @Nullable
    public ResourceLocation modifier() {
        return this.modifier;
    }
}
