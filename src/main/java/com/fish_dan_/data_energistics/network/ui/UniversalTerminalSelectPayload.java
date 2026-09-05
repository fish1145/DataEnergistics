package com.fish_dan_.data_energistics.network.ui;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.menu.universal.UniversalTerminalMenuBridge;
import com.fish_dan_.data_energistics.menu.universal.UniversalTerminalMenuLocator;
import com.fish_dan_.data_energistics.menu.universal.UniversalTerminalMenuSupport;
import com.fish_dan_.data_energistics.part.UniversalTerminalPart;

import appeng.menu.AEBaseMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UniversalTerminalSelectPayload(String terminalName) implements CustomPacketPayload {

    public static final Type<UniversalTerminalSelectPayload> TYPE = new Type<>(Data_Energistics.id("universal_terminal_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UniversalTerminalSelectPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            UniversalTerminalSelectPayload::terminalName,
            UniversalTerminalSelectPayload::new);

    @Override
    public Type<UniversalTerminalSelectPayload> type() {
        return TYPE;
    }

    public static void handle(UniversalTerminalSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            AbstractContainerMenu menu = player.containerMenu;
            if (menu instanceof UniversalTerminalMenuBridge bridge) {
                UniversalTerminalMenuSupport.switchTerminal(bridge.getUniversalTerminalHost(), player, payload.terminalName());
            } else if (menu instanceof AEBaseMenu aeBaseMenu && aeBaseMenu.getLocator() instanceof UniversalTerminalMenuLocator locator) {
                UniversalTerminalPart host = locator.locate(player, UniversalTerminalPart.class);
                if (host != null) {
                    UniversalTerminalMenuSupport.switchTerminal(host, player, payload.terminalName());
                }
            }
        });
    }
}
