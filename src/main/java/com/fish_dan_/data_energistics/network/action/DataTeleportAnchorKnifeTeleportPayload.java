package com.fish_dan_.data_energistics.network.action;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.block.machine.DataTeleportAnchorBlock;
import com.fish_dan_.data_energistics.blockentity.machine.DataTeleportAnchorBlockEntity;
import com.fish_dan_.data_energistics.item.powered.PoweredCuttingKnifeItem;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DataTeleportAnchorKnifeTeleportPayload(BlockPos anchorPos, boolean offHand)
        implements CustomPacketPayload {

    public static final Type<DataTeleportAnchorKnifeTeleportPayload> TYPE = new Type<>(Data_Energistics.id("data_teleport_anchor_knife_teleport"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DataTeleportAnchorKnifeTeleportPayload> STREAM_CODEC = CustomPacketPayload.codec(DataTeleportAnchorKnifeTeleportPayload::write, DataTeleportAnchorKnifeTeleportPayload::new);

    private DataTeleportAnchorKnifeTeleportPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(this.anchorPos);
        buf.writeBoolean(this.offHand);
    }

    @Override
    public Type<DataTeleportAnchorKnifeTeleportPayload> type() {
        return TYPE;
    }

    public static void handle(DataTeleportAnchorKnifeTeleportPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            var level = player.level();
            InteractionHand hand = payload.offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (!(stack.getItem() instanceof PoweredCuttingKnifeItem)) {
                return;
            }

            BlockEntity blockEntity = level.getBlockEntity(payload.anchorPos);
            if (!(blockEntity instanceof DataTeleportAnchorBlockEntity anchor)) {
                return;
            }
            if (!(level.getBlockState(payload.anchorPos).getBlock() instanceof DataTeleportAnchorBlock)) {
                return;
            }

            DataTeleportAnchorBlock.tryHandleCuttingKnifeTeleportToAnchor(stack, level, payload.anchorPos, player, anchor);
        });
    }
}
