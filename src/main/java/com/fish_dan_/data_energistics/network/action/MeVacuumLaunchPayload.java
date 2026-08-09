package com.fish_dan_.data_energistics.network.action;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.item.vacuum.MeVacuumItem;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MeVacuumLaunchPayload(boolean offHand) implements CustomPacketPayload {

    public static final Type<MeVacuumLaunchPayload> TYPE = new Type<>(Data_Energistics.id("me_vacuum_launch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MeVacuumLaunchPayload> STREAM_CODEC = CustomPacketPayload.codec(MeVacuumLaunchPayload::write, MeVacuumLaunchPayload::new);

    private MeVacuumLaunchPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(this.offHand);
    }

    @Override
    public Type<MeVacuumLaunchPayload> type() {
        return TYPE;
    }

    public static void handle(MeVacuumLaunchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            InteractionHand hand = payload.offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof MeVacuumItem meVacuumItem) {
                meVacuumItem.tryLaunchTrackedEntity(stack, player);
            }
        });
    }
}
