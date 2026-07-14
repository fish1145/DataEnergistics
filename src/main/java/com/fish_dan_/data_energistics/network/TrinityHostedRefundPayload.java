package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.TrinityHostedActionTicket;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.TrinityDataCoreHostUiKeys;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C2S refund action bound to the exact open crafting-window generation. */
public record TrinityHostedRefundPayload(int containerId,
                                         long generation,
                                         long actionSequence)
        implements CustomPacketPayload {

    public static final Type<TrinityHostedRefundPayload> TYPE = new Type<>(
            Data_Energistics.id("trinity_hosted_refund"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityHostedRefundPayload> STREAM_CODEC = CustomPacketPayload.codec(TrinityHostedRefundPayload::write, TrinityHostedRefundPayload::new);

    /** Rejects invalid envelope and ticket values before transport. */
    public TrinityHostedRefundPayload {
        if (containerId < 0 || containerId > TrinityHostedActionPayloadCodec.MAX_CONTAINER_ID) {
            throw new IllegalArgumentException("Invalid Trinity hosted refund container id: " + containerId);
        }
        new TrinityHostedActionTicket(TrinityDataCoreHostUiKeys.CRAFTING, generation, actionSequence);
    }

    private TrinityHostedRefundPayload(RegistryFriendlyByteBuf buffer) {
        this(
                TrinityHostedActionPayloadCodec.readContainerId(buffer),
                TrinityHostedActionPayloadCodec.readGeneration(buffer),
                TrinityHostedActionPayloadCodec.readSequence(buffer));
        TrinityHostedActionPayloadCodec.requireFullyConsumed(buffer);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        TrinityHostedActionPayloadCodec.writeContainerId(buffer, this.containerId);
        TrinityHostedActionPayloadCodec.writeTicket(buffer, this.generation, this.actionSequence);
    }

    /** Returns the fixed crafting-window identity carried by this request. */
    public TrinityHostedActionTicket ticket() {
        return new TrinityHostedActionTicket(
                TrinityDataCoreHostUiKeys.CRAFTING,
                this.generation,
                this.actionSequence);
    }

    @Override
    public Type<TrinityHostedRefundPayload> type() {
        return TYPE;
    }

    /** Defers authoritative validation and execution to the server main thread. */
    public static void handle(TrinityHostedRefundPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TrinityHostedActionPayloadHandler.handleRefund(payload, context.player()));
    }
}
