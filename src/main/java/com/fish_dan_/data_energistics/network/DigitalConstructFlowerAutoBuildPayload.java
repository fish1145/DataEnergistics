package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.DigitalConstructFlowerBlockEntity;
import com.fish_dan_.data_energistics.menu.DigitalConstructFlowerMenu;
import com.fish_dan_.data_energistics.menu.DigitalConstructFlowerMenuHost;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DigitalConstructFlowerAutoBuildPayload(DigitalConstructFlowerAutoBuildTarget target) implements CustomPacketPayload {

    public static final Type<DigitalConstructFlowerAutoBuildPayload> TYPE = new Type<>(Data_Energistics.id("digital_construct_flower_auto_build"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DigitalConstructFlowerAutoBuildPayload> STREAM_CODEC = CustomPacketPayload.codec(
            DigitalConstructFlowerAutoBuildPayload::write,
            DigitalConstructFlowerAutoBuildPayload::new);

    private DigitalConstructFlowerAutoBuildPayload(RegistryFriendlyByteBuf buf) {
        this(DigitalConstructFlowerAutoBuildTarget.read(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        DigitalConstructFlowerAutoBuildTarget.write(buf, this.target);
    }

    @Override
    public Type<DigitalConstructFlowerAutoBuildPayload> type() {
        return TYPE;
    }

    public static void handle(DigitalConstructFlowerAutoBuildPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            AbstractContainerMenu menu = serverPlayer.containerMenu;
            if (!(menu instanceof DigitalConstructFlowerMenu flowerMenu)) {
                return;
            }

            DigitalConstructFlowerMenuHost host = flowerMenu.getHost();
            if (host instanceof DigitalConstructFlowerBlockEntity flower) {
                flower.autoBuildTrinityStructure(serverPlayer, payload.target);
            }
        });
    }
}
