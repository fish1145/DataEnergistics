package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.TrinityDataCoreBlockEntity;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenuHost;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TrinityDataCoreAutoBuildPayload(TrinityDataCoreAutoBuildTarget target) implements CustomPacketPayload {

    public static final Type<TrinityDataCoreAutoBuildPayload> TYPE = new Type<>(Data_Energistics.id("trinity_data_core_auto_build"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityDataCoreAutoBuildPayload> STREAM_CODEC = CustomPacketPayload.codec(
            TrinityDataCoreAutoBuildPayload::write,
            TrinityDataCoreAutoBuildPayload::new);

    private TrinityDataCoreAutoBuildPayload(RegistryFriendlyByteBuf buf) {
        this(TrinityDataCoreAutoBuildTarget.read(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        TrinityDataCoreAutoBuildTarget.write(buf, this.target);
    }

    @Override
    public Type<TrinityDataCoreAutoBuildPayload> type() {
        return TYPE;
    }

    public static void handle(TrinityDataCoreAutoBuildPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }

            AbstractContainerMenu menu = serverPlayer.containerMenu;
            if (!(menu instanceof TrinityDataCoreMenu trinityMenu)) {
                return;
            }

            TrinityDataCoreMenuHost host = trinityMenu.getHost();
            if (host instanceof TrinityDataCoreBlockEntity trinityDataCore) {
                trinityDataCore.autoBuildTrinityStructure(serverPlayer, payload.target);
            }
        });
    }
}
