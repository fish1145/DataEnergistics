package com.fish_dan_.data_energistics.network.ui;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiCoordinator;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiCoordinatorHolder;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiRequest;
import com.fish_dan_.data_energistics.gui.ldlib2.HostUiResponse;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S envelope binding one ordered host UI request to the menu that created its coordinator. */
public record HostUiRequestPayload(int containerId, HostUiRequest request) implements CustomPacketPayload {

    public static final Type<HostUiRequestPayload> TYPE = new Type<>(Data_Energistics.id("host_ui_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HostUiRequestPayload> STREAM_CODEC = CustomPacketPayload.codec(HostUiRequestPayload::write, HostUiRequestPayload::new);

    /** Rejects an invalid envelope before it can be routed to a menu. */
    public HostUiRequestPayload {
        if (containerId < 0) {
            throw new IllegalArgumentException("Host UI container id must not be negative: " + containerId);
        }
        if (request == null) {
            throw new IllegalArgumentException("Host UI request must not be null");
        }
    }

    private HostUiRequestPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readVarInt(), HostUiPayloadCodec.readRequest(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.containerId);
        HostUiPayloadCodec.writeRequest(buffer, this.request);
    }

    @Override
    public Type<HostUiRequestPayload> type() {
        return TYPE;
    }

    /** Routes the request only to the server player's exact current menu and coordinator. */
    public static void handle(HostUiRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleOnMainThread(payload, context.player()));
    }

    /** Applies validated routing and closes a menu whose coordinator detects any topology divergence. */
    static void handleOnMainThread(HostUiRequestPayload payload, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            Data_Energistics.LOGGER.error("Rejected a host UI request outside a server player context");
            return;
        }

        AbstractContainerMenu menu = serverPlayer.containerMenu;
        if (menu.containerId != payload.containerId) {
            Data_Energistics.LOGGER.warn(
                    "Ignored host UI request for stale container {} while player {} has container {}",
                    payload.containerId,
                    serverPlayer.getGameProfile().getName(),
                    menu.containerId);
            return;
        }
        if (!(menu instanceof HostUiCoordinatorHolder holder)) {
            Data_Energistics.LOGGER.warn(
                    "Ignored host UI request for container {} without a coordinator holder",
                    payload.containerId);
            return;
        }

        HostUiCoordinator coordinator = holder.getHostUiCoordinator();
        if (coordinator == null) {
            Data_Energistics.LOGGER.error("Host UI coordinator holder returned null for container {}", payload.containerId);
            serverPlayer.closeContainer();
            return;
        }

        boolean hostAvailable;
        try {
            hostAvailable = holder.isHostUiAvailable(serverPlayer);
        } catch (RuntimeException | Error failure) {
            Data_Energistics.LOGGER.error(
                    "Failed to validate host UI request for player {} and container {}",
                    serverPlayer.getGameProfile().getName(),
                    payload.containerId,
                    failure);
            hostAvailable = false;
        }

        HostUiResponse response;
        try {
            response = coordinator.handleRequest(payload.request, hostAvailable);
        } catch (RuntimeException | Error failure) {
            Data_Energistics.LOGGER.error(
                    "Failed to handle host UI request for player {} and container {}",
                    serverPlayer.getGameProfile().getName(),
                    payload.containerId,
                    failure);
            serverPlayer.closeContainer();
            return;
        }

        try {
            PacketDistributor.sendToPlayer(serverPlayer, new HostUiResponsePayload(payload.containerId, response));
        } catch (RuntimeException | Error failure) {
            Data_Energistics.LOGGER.error(
                    "Failed to send host UI response to player {} for container {}, operation {}, key {}, sequence {}",
                    serverPlayer.getGameProfile().getName(),
                    payload.containerId,
                    payload.request.operation(),
                    payload.request.key().id(),
                    payload.request.sequence(),
                    failure);
            serverPlayer.closeContainer();
            return;
        }
        if (coordinator.isTerminal()) {
            serverPlayer.closeContainer();
        }
    }
}
