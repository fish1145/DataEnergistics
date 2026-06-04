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

public record DigitalStorageDepotBucketModePayload(boolean offHand) implements CustomPacketPayload {

    public static final Type<DigitalStorageDepotBucketModePayload> TYPE = new Type<>(Data_Energistics.id("digital_storage_depot_bucket_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DigitalStorageDepotBucketModePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            DigitalStorageDepotBucketModePayload::offHand,
            DigitalStorageDepotBucketModePayload::new);

    @Override
    public Type<DigitalStorageDepotBucketModePayload> type() {
        return TYPE;
    }

    public static void handle(DigitalStorageDepotBucketModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            InteractionHand hand = payload.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!DigitalStorageDepotBlockItem.isDepotStack(stack)) {
                return;
            }

            ItemStack updatedStack = stack.copy();
            boolean bucketMode = DigitalStorageDepotBlockItem.toggleBucketMode(updatedStack);
            player.setItemInHand(hand, updatedStack);
            player.displayClientMessage(Component.translatable(
                    bucketMode ? "message.data_energistics.digital_storage_depot.bucket_mode_on" : "message.data_energistics.digital_storage_depot.bucket_mode_off"), true);
        });
    }
}
