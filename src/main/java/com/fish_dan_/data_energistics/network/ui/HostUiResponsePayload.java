package com.fish_dan_.data_energistics.network.ui;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiCoordinator;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiCoordinatorHolder;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiResponse;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C envelope that lets only the originating client menu mirror an authoritative lifecycle operation. */
public record HostUiResponsePayload(int containerId, HostUiResponse response) implements CustomPacketPayload {

    public static final Type<HostUiResponsePayload> TYPE = new Type<>(Data_Energistics.id("host_ui_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HostUiResponsePayload> STREAM_CODEC = CustomPacketPayload.codec(HostUiResponsePayload::write, HostUiResponsePayload::new);

    /** Rejects an invalid envelope before it can be routed to a client menu. */
    public HostUiResponsePayload {
        if (containerId < 0) {
            throw new IllegalArgumentException("Host UI container id must not be negative: " + containerId);
        }
        if (response == null) {
            throw new IllegalArgumentException("Host UI response must not be null");
        }
    }

    private HostUiResponsePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), HostUiPayloadCodec.readResponse(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.containerId);
        HostUiPayloadCodec.writeResponse(buffer, this.response);
    }

    @Override
    public Type<HostUiResponsePayload> type() {
        return TYPE;
    }

    /** Routes the response only to the exact current client menu. */
    public static void handle(HostUiResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleOnMainThread(payload, context.player()));
    }

    /** Applies one response and closes the client menu if its topology can no longer be synchronized. */
    static void handleOnMainThread(HostUiResponsePayload payload, Player player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != payload.containerId || !(menu instanceof HostUiCoordinatorHolder holder)) {
            Data_Energistics.LOGGER.warn(
                    "Ignored host UI response for stale or unsupported container {}",
                    payload.containerId);
            return;
        }

        HostUiCoordinator coordinator = holder.getHostUiCoordinator();
        if (coordinator == null) {
            Data_Energistics.LOGGER.error("Host UI coordinator holder returned null for container {}", payload.containerId);
            player.closeContainer();
            return;
        }

        try {
            coordinator.handleResponse(payload.response);
        } catch (RuntimeException | Error failure) {
            Data_Energistics.LOGGER.error(
                    "Failed to handle host UI response for container {}",
                    payload.containerId,
                    failure);
            player.closeContainer();
            return;
        }
        if (coordinator.isTerminal() && player.containerMenu == menu) {
            player.closeContainer();
        }
    }
}
