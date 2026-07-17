package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionResult;
import com.fish_dan_.data_energistics.menu.TrinityDataCoreMenu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C terminal result that can clear only its exact originating client ticket. */
public record TrinityHostedActionResponsePayload(int containerId,
                                                 TrinityHostedActionResult result)
        implements CustomPacketPayload {

    public static final Type<TrinityHostedActionResponsePayload> TYPE = new Type<>(
            Data_Energistics.id("trinity_hosted_action_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityHostedActionResponsePayload> STREAM_CODEC = CustomPacketPayload.codec(
            TrinityHostedActionResponsePayload::write,
            TrinityHostedActionResponsePayload::new);

    /** Rejects an invalid response envelope before transport. */
    public TrinityHostedActionResponsePayload {
        if (containerId < 0 || containerId > TrinityHostedActionPayloadCodec.MAX_CONTAINER_ID) {
            throw new IllegalArgumentException("Invalid Trinity hosted response container id: " + containerId);
        }
        if (result == null) {
            throw new IllegalArgumentException("Trinity hosted response result cannot be null");
        }
    }

    private TrinityHostedActionResponsePayload(RegistryFriendlyByteBuf buffer) {
        this(
                TrinityHostedActionPayloadCodec.readContainerId(buffer),
                TrinityHostedActionPayloadCodec.readResult(buffer));
        TrinityHostedActionPayloadCodec.requireFullyConsumed(buffer);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        TrinityHostedActionPayloadCodec.writeContainerId(buffer, this.containerId);
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
        trinityMenu.handleHostedActionResponse(payload.result);
    }
}
