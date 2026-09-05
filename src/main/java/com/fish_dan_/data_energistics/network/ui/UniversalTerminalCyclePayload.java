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

public record UniversalTerminalCyclePayload(boolean reverse) implements CustomPacketPayload {

    public static final Type<UniversalTerminalCyclePayload> TYPE = new Type<>(Data_Energistics.id("universal_terminal_cycle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UniversalTerminalCyclePayload> STREAM_CODEC = CustomPacketPayload.codec(UniversalTerminalCyclePayload::write, UniversalTerminalCyclePayload::new);

    private UniversalTerminalCyclePayload(RegistryFriendlyByteBuf buf) {
        this(ByteBufCodecs.BOOL.decode(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        ByteBufCodecs.BOOL.encode(buf, this.reverse);
    }

    @Override
    public Type<UniversalTerminalCyclePayload> type() {
        return TYPE;
    }

    public static void handle(UniversalTerminalCyclePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            AbstractContainerMenu menu = player.containerMenu;
            if (menu instanceof UniversalTerminalMenuBridge bridge) {
                UniversalTerminalMenuSupport.cycleTerminal(bridge.getUniversalTerminalHost(), player, payload.reverse());
            } else if (menu instanceof AEBaseMenu aeBaseMenu && aeBaseMenu.getLocator() instanceof UniversalTerminalMenuLocator locator) {
                UniversalTerminalPart host = locator.locate(player, UniversalTerminalPart.class);
                if (host != null) {
                    UniversalTerminalMenuSupport.cycleTerminal(host, player, payload.reverse());
                }
            }
        });
    }
}
