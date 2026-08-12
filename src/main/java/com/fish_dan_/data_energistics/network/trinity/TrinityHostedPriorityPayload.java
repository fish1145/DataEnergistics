package com.fish_dan_.data_energistics.network.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionTicket;
import com.fish_dan_.data_energistics.gui.ldlib2.host.protocol.HostUiKey;
import com.fish_dan_.data_energistics.gui.ldlib2.priority.PriorityControl;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** C2S server-authoritative priority operation from one exact hosted editor generation. */
public record TrinityHostedPriorityPayload(int containerId,
                                           UUID hostId,
                                           UUID menuSessionId,
                                           HostUiKey key,
                                           long generation,
                                           long actionSequence,
                                           PriorityControl.Operation operation)
        implements CustomPacketPayload {

    public static final Type<TrinityHostedPriorityPayload> TYPE = new Type<>(
            Data_Energistics.id("trinity_hosted_priority"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityHostedPriorityPayload> STREAM_CODEC = CustomPacketPayload.codec(TrinityHostedPriorityPayload::write, TrinityHostedPriorityPayload::new);

    /** Rejects invalid envelopes, keys, tickets, and operation variants before transport. */
    public TrinityHostedPriorityPayload {
        if (containerId < 0 || containerId > TrinityHostedActionPayloadCodec.MAX_CONTAINER_ID || hostId == null ||
                menuSessionId == null) {
            throw new IllegalArgumentException("Invalid Trinity hosted priority envelope");
        }
        TrinityHostedActionPayloadCodec.requirePriorityKey(key);
        new TrinityHostedActionTicket(key, generation, actionSequence);
        TrinityHostedActionPayloadCodec.requirePriorityOperation(operation);
    }

    private TrinityHostedPriorityPayload(RegistryFriendlyByteBuf buffer) {
        this(
                TrinityHostedActionPayloadCodec.readContainerId(buffer),
                TrinityHostedActionPayloadCodec.readUuid(buffer, "host id"),
                TrinityHostedActionPayloadCodec.readUuid(buffer, "menu session id"),
                TrinityHostedActionPayloadCodec.readPriorityKey(buffer),
                TrinityHostedActionPayloadCodec.readGeneration(buffer),
                TrinityHostedActionPayloadCodec.readSequence(buffer),
                TrinityHostedActionPayloadCodec.readPriorityOperation(buffer));
        TrinityHostedActionPayloadCodec.requireFullyConsumed(buffer);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        TrinityHostedActionPayloadCodec.writeContainerId(buffer, this.containerId);
        TrinityHostedActionPayloadCodec.writeUuid(buffer, this.hostId, "host id");
        TrinityHostedActionPayloadCodec.writeUuid(buffer, this.menuSessionId, "menu session id");
        TrinityHostedActionPayloadCodec.writePriorityKey(buffer, this.key);
        TrinityHostedActionPayloadCodec.writeTicket(buffer, this.generation, this.actionSequence);
        TrinityHostedActionPayloadCodec.writePriorityOperation(buffer, this.operation);
    }

    /** Returns the exact hosted priority ticket carried by this request. */
    public TrinityHostedActionTicket ticket() {
        return new TrinityHostedActionTicket(this.key, this.generation, this.actionSequence);
    }

    @Override
    public Type<TrinityHostedPriorityPayload> type() {
        return TYPE;
    }

    /** Defers authoritative validation and execution to the server main thread. */
    public static void handle(TrinityHostedPriorityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TrinityHostedActionPayloadHandler.handlePriority(payload, context.player()));
    }
}
