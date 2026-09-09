package com.fish_dan_.data_energistics.network.action;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MatterConvergingCrossbowAmmoPayload(boolean offHand, ResourceLocation itemId) implements CustomPacketPayload {
    public static final Type<MatterConvergingCrossbowAmmoPayload> TYPE = new Type<>(Data_Energistics.id("dark_string_data_settlement_tool_ammo"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MatterConvergingCrossbowAmmoPayload> STREAM_CODEC = CustomPacketPayload.codec(
            MatterConvergingCrossbowAmmoPayload::write, MatterConvergingCrossbowAmmoPayload::new);

    private void write(RegistryFriendlyByteBuf buffer) { buffer.writeBoolean(offHand); buffer.writeResourceLocation(itemId); }
    private MatterConvergingCrossbowAmmoPayload(RegistryFriendlyByteBuf buffer) { this(buffer.readBoolean(), buffer.readResourceLocation()); }
    @Override public Type<MatterConvergingCrossbowAmmoPayload> type() { return TYPE; }

    public static void handle(MatterConvergingCrossbowAmmoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            InteractionHand hand = payload.offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = context.player().getItemInHand(hand);
            if (stack.getItem() instanceof MatterConvergingCrossbowItem item) item.selectCannonAmmo(stack, payload.itemId);
        });
    }
}
