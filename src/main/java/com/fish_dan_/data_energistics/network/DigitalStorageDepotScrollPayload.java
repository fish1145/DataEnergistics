package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.DigitalStorageDepotBlockItem;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DigitalStorageDepotScrollPayload(boolean reverse, boolean offHand,
                                               boolean keySlot)
        implements CustomPacketPayload {

    public static final Type<DigitalStorageDepotScrollPayload> TYPE = new Type<>(Data_Energistics.id("digital_storage_depot_scroll"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DigitalStorageDepotScrollPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            DigitalStorageDepotScrollPayload::reverse,
            ByteBufCodecs.BOOL,
            DigitalStorageDepotScrollPayload::offHand,
            ByteBufCodecs.BOOL,
            DigitalStorageDepotScrollPayload::keySlot,
            DigitalStorageDepotScrollPayload::new);

    @Override
    public Type<DigitalStorageDepotScrollPayload> type() {
        return TYPE;
    }

    public static void handle(DigitalStorageDepotScrollPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            InteractionHand hand = payload.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!DigitalStorageDepotBlockItem.isDepotStack(stack)) {
                return;
            }
            if (payload.keySlot()) {
                int slot = DigitalStorageDepotBlockItem.cycleSelectedKeySlot(stack, payload.reverse());
                player.displayClientMessage(Component.literal("当前 Key 槽: " + (slot + 1)), true);
            } else {
                int slot = DigitalStorageDepotBlockItem.cycleSelectedFluidSlot(stack, payload.reverse());
                player.displayClientMessage(Component.literal("当前流体槽: " + (slot + 1)), true);
            }
        });
    }
}
