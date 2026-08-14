package com.fish_dan_.data_energistics.network.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionTicket;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityPatternSlotAction;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.core.TrinityDataCoreHostUiKeys;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** C2S revision-bound click on one global slot in the aggregate Trinity pattern catalog. */
public record TrinityHostedPatternSlotPayload(int containerId,
                                              UUID hostId,
                                              UUID menuSessionId,
                                              long generation,
                                              long actionSequence,
                                              long layoutRevision,
                                              long catalogRevision,
                                              int globalSlot,
                                              TrinityPatternSlotAction action)
        implements CustomPacketPayload {

    public static final Type<TrinityHostedPatternSlotPayload> TYPE = new Type<>(
            Data_Energistics.id("trinity_hosted_pattern_slot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityHostedPatternSlotPayload> STREAM_CODEC = CustomPacketPayload.codec(TrinityHostedPatternSlotPayload::write, TrinityHostedPatternSlotPayload::new);

    public TrinityHostedPatternSlotPayload {
        if (containerId < 0 || containerId > TrinityHostedActionPayloadCodec.MAX_CONTAINER_ID || hostId == null ||
                menuSessionId == null || layoutRevision < 0L || catalogRevision < 0L || globalSlot < 0 ||
                action == null) {
            throw new IllegalArgumentException("Invalid Trinity hosted pattern-slot envelope");
        }
        new TrinityHostedActionTicket(TrinityDataCoreHostUiKeys.PATTERN, generation, actionSequence);
    }

    private TrinityHostedPatternSlotPayload(RegistryFriendlyByteBuf buffer) {
        this(
                TrinityHostedActionPayloadCodec.readContainerId(buffer),
                TrinityHostedActionPayloadCodec.readUuid(buffer, "host id"),
                TrinityHostedActionPayloadCodec.readUuid(buffer, "menu session id"),
                TrinityHostedActionPayloadCodec.readGeneration(buffer),
                TrinityHostedActionPayloadCodec.readSequence(buffer),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarInt(),
                TrinityHostedActionPayloadCodec.readPatternSlotAction(buffer));
        TrinityHostedActionPayloadCodec.requireFullyConsumed(buffer);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        TrinityHostedActionPayloadCodec.writeContainerId(buffer, this.containerId);
        TrinityHostedActionPayloadCodec.writeUuid(buffer, this.hostId, "host id");
        TrinityHostedActionPayloadCodec.writeUuid(buffer, this.menuSessionId, "menu session id");
        TrinityHostedActionPayloadCodec.writeTicket(buffer, this.generation, this.actionSequence);
        buffer.writeVarLong(this.layoutRevision);
        buffer.writeVarLong(this.catalogRevision);
        buffer.writeVarInt(this.globalSlot);
        TrinityHostedActionPayloadCodec.writePatternSlotAction(buffer, this.action);
    }

    public TrinityHostedActionTicket ticket() {
        return new TrinityHostedActionTicket(
                TrinityDataCoreHostUiKeys.PATTERN,
                this.generation,
                this.actionSequence);
    }

    @Override
    public Type<TrinityHostedPatternSlotPayload> type() {
        return TYPE;
    }

    public static void handle(TrinityHostedPatternSlotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TrinityHostedActionPayloadHandler.handlePatternSlot(payload, context.player()));
    }
}
