package com.fish_dan_.data_energistics.network.action;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowItem;
import com.fish_dan_.data_energistics.item.powered.MatterConvergingCrossbowMode;
import com.fish_dan_.data_energistics.registry.DEDataComponents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MatterConvergingCrossbowModePayload(boolean offHand, MatterConvergingCrossbowMode mode) implements CustomPacketPayload {

    public static final Type<MatterConvergingCrossbowModePayload> TYPE = new Type<>(Data_Energistics.id("dark_string_data_settlement_tool_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MatterConvergingCrossbowModePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, MatterConvergingCrossbowModePayload::offHand,
            ByteBufCodecs.VAR_INT.map(MatterConvergingCrossbowMode::fromId, MatterConvergingCrossbowMode::id), MatterConvergingCrossbowModePayload::mode,
            MatterConvergingCrossbowModePayload::new);

    @Override
    public Type<MatterConvergingCrossbowModePayload> type() {
        return TYPE;
    }

    public static void handle(MatterConvergingCrossbowModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            MatterConvergingCrossbowMode mode = payload.mode;
            Player player = context.player();
            InteractionHand hand = payload.offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof MatterConvergingCrossbowItem)) return;
            ItemStack updated = stack.copy();
            updated.set(DEDataComponents.MATTER_CONVERGING_CROSSBOW_MODE.get(), mode.id());
            player.setItemInHand(hand, updated);
        });
    }
}
