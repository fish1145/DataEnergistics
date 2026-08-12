package com.fish_dan_.data_energistics.network.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionResult;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.core.TrinityDataCoreHostUiKeys;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * S2C terminal result that can clear only its exact host, menu session, and client ticket.
 */
public record TrinityHostedActionResponsePayload(int containerId,
                                                 UUID hostId,
                                                 UUID menuSessionId,
                                                 TrinityHostedActionResult result)
        implements CustomPacketPayload {

    public static final Type<TrinityHostedActionResponsePayload> TYPE = new Type<>(
            Data_Energistics.id("trinity_hosted_action_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityHostedActionResponsePayload> STREAM_CODEC = CustomPacketPayload.codec(
            TrinityHostedActionResponsePayload::write,
            TrinityHostedActionResponsePayload::new);

    /**
     * Rejects an invalid response envelope before transport.
     */
    public TrinityHostedActionResponsePayload {
        if (containerId < 0 || containerId > TrinityHostedActionPayloadCodec.MAX_CONTAINER_ID) {
            throw new IllegalArgumentException("Invalid Trinity hosted response envelope");
        }
    }

    private TrinityHostedActionResponsePayload(RegistryFriendlyByteBuf buffer) {
        this(
                TrinityHostedActionPayloadCodec.readContainerId(buffer),
                TrinityHostedActionPayloadCodec.readUuid(buffer, "host id"),
                TrinityHostedActionPayloadCodec.readUuid(buffer, "menu session id"),
                TrinityHostedActionPayloadCodec.readResult(buffer));
        TrinityHostedActionPayloadCodec.requireFullyConsumed(buffer);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        TrinityHostedActionPayloadCodec.writeContainerId(buffer, this.containerId);
        TrinityHostedActionPayloadCodec.writeUuid(buffer, this.hostId, "host id");
        TrinityHostedActionPayloadCodec.writeUuid(buffer, this.menuSessionId, "menu session id");
        TrinityHostedActionPayloadCodec.writeResult(buffer, this.result);
    }

    @Override
    public Type<TrinityHostedActionResponsePayload> type() {
        return TYPE;
    }

    /**
     * Defers exact current-menu and pending-ticket matching to the client main thread.
     */
    public static void handle(TrinityHostedActionResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleOnMainThread(payload, context.player()));
    }

    /**
     * Applies a response only to its exact current Trinity menu.
     */
    static void handleOnMainThread(TrinityHostedActionResponsePayload payload, Player player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != payload.containerId || !(menu instanceof TrinityDataCoreMenu trinityMenu)) {
            Data_Energistics.LOGGER.warn(
                    "Ignored Trinity hosted action response for stale container {}, current menu {}",
                    payload.containerId,
                    menu.getClass().getName());
            return;
        }
        if (!trinityMenu.handleHostedActionResponse(payload.hostId, payload.menuSessionId, payload.result)) {
            Data_Energistics.LOGGER.warn(
                    "Ignored Trinity hosted action response for stale host/session: container={}, host={}, session={}",
                    payload.containerId,
                    payload.hostId,
                    payload.menuSessionId);
            return;
        }
        if (TrinityDataCoreHostUiKeys.AUTO_BUILD.equals(payload.result.key())) {
            handleAutoBuildResult(trinityMenu, player, payload.result);
        }
    }

    private static void handleAutoBuildResult(TrinityDataCoreMenu menu,
                                              Player player,
                                              TrinityHostedActionResult response) {
        TrinityHostedActionResult result = menu.consumeHostedActionResult(response.key(), response.generation());
        if (!response.equals(result)) {
            Data_Energistics.LOGGER.error(
                    "Trinity auto-build response was accepted but its retained result diverged: response={}, retained={}",
                    response,
                    result);
            player.displayClientMessage(
                    Component.translatable("message.data_energistics.trinity_data_core.auto_build.internal_error"),
                    false);
            player.closeContainer();
            return;
        }
        switch (result.status()) {
            case COMPLETED -> {
                // The builder already reports exact placement counts and failures to the player.
            }
            case STALE_STATE -> {
                player.displayClientMessage(
                        Component.translatable("message.data_energistics.trinity_data_core.auto_build.stale"),
                        false);
                player.closeContainer();
            }
            case REJECTED -> player.displayClientMessage(
                    Component.translatable("message.data_energistics.trinity_data_core.auto_build.rejected"),
                    false);
            case INTERNAL_ERROR -> player.displayClientMessage(
                    Component.translatable("message.data_energistics.trinity_data_core.auto_build.internal_error"),
                    false);
            case NO_OP, DELIVERY_FAILED -> {
                Data_Energistics.LOGGER.error("Unexpected Trinity auto-build result status: {}", result.status());
                player.displayClientMessage(
                        Component.translatable("message.data_energistics.trinity_data_core.auto_build.internal_error"),
                        false);
            }
        }
    }
}
