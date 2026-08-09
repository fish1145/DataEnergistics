package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionTicket;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.TrinityDataCoreHostUiKeys;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** C2S request to return queued inputs and pending outputs from one exact menu session. */
public record TrinityRefundRetainedItemsPayload(int containerId,
                                                UUID hostId,
                                                UUID menuSessionId,
                                                long actionSequence)
        implements CustomPacketPayload {

    public static final Type<TrinityRefundRetainedItemsPayload> TYPE = new Type<>(
            Data_Energistics.id("trinity_refund_retained_items"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityRefundRetainedItemsPayload> STREAM_CODEC = CustomPacketPayload.codec(TrinityRefundRetainedItemsPayload::write, TrinityRefundRetainedItemsPayload::new);

    /** Validates the static action envelope before it reaches the server main thread. */
    public TrinityRefundRetainedItemsPayload {
        if (containerId < 0 || containerId > TrinityHostedActionPayloadCodec.MAX_CONTAINER_ID || hostId == null ||
                menuSessionId == null) {
            throw new IllegalArgumentException("Invalid Trinity retained-item refund envelope");
        }
        new TrinityHostedActionTicket(
                TrinityDataCoreHostUiKeys.REFUND_RETAINED_ITEMS,
                1L,
                actionSequence);
    }

    private TrinityRefundRetainedItemsPayload(RegistryFriendlyByteBuf buffer) {
        this(
                TrinityHostedActionPayloadCodec.readContainerId(buffer),
                TrinityHostedActionPayloadCodec.readUuid(buffer, "host id"),
                TrinityHostedActionPayloadCodec.readUuid(buffer, "menu session id"),
                TrinityHostedActionPayloadCodec.readSequence(buffer));
        TrinityHostedActionPayloadCodec.requireFullyConsumed(buffer);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        TrinityHostedActionPayloadCodec.writeContainerId(buffer, this.containerId);
        TrinityHostedActionPayloadCodec.writeUuid(buffer, this.hostId, "host id");
        TrinityHostedActionPayloadCodec.writeUuid(buffer, this.menuSessionId, "menu session id");
        TrinityHostedActionPayloadCodec.writeSequence(buffer, this.actionSequence);
    }

    /** Returns the fixed static-action ticket, isolated by host and menu session envelope identities. */
    public TrinityHostedActionTicket ticket() {
        return new TrinityHostedActionTicket(
                TrinityDataCoreHostUiKeys.REFUND_RETAINED_ITEMS,
                1L,
                this.actionSequence);
    }

    @Override
    public Type<TrinityRefundRetainedItemsPayload> type() {
        return TYPE;
    }

    /** Defers validation and retained-work mutation to the server main thread. */
    public static void handle(TrinityRefundRetainedItemsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TrinityHostedActionPayloadHandler.handleRefundRetainedItems(payload, context.player()));
    }
}
