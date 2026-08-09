package com.fish_dan_.data_energistics.network.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionResult;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** S2C terminal result that can clear only its exact host, menu session, and client ticket. */
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

    /** Rejects an invalid response envelope before transport. */
    public TrinityHostedActionResponsePayload {
        if (containerId < 0 || containerId > TrinityHostedActionPayloadCodec.MAX_CONTAINER_ID || hostId == null ||
                menuSessionId == null) {
            throw new IllegalArgumentException("Invalid Trinity hosted response envelope");
        }
        if (result == null) {
            throw new IllegalArgumentException("Trinity hosted response result cannot be null");
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

    /** Defers exact current-menu and pending-ticket matching to the client main thread. */
    public static void handle(TrinityHostedActionResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleOnMainThread(payload, context.player()));
    }

    /** Applies a response only to its exact current Trinity menu. */
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
        }
    }
}
