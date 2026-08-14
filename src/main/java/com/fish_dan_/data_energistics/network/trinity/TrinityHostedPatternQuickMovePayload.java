package com.fish_dan_.data_energistics.network.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionTicket;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.core.TrinityDataCoreHostUiKeys;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;
import java.util.UUID;

/** C2S ordered batch produced by one Shift-drag over aggregate Trinity pattern slots. */
public record TrinityHostedPatternQuickMovePayload(int containerId,
                                                   UUID hostId,
                                                   UUID menuSessionId,
                                                   long generation,
                                                   long actionSequence,
                                                   long layoutRevision,
                                                   List<Integer> globalSlots)
        implements CustomPacketPayload {

    public static final Type<TrinityHostedPatternQuickMovePayload> TYPE = new Type<>(
            Data_Energistics.id("trinity_hosted_pattern_quick_move"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityHostedPatternQuickMovePayload> STREAM_CODEC = CustomPacketPayload.codec(TrinityHostedPatternQuickMovePayload::write,
            TrinityHostedPatternQuickMovePayload::new);

    public TrinityHostedPatternQuickMovePayload {
        if (containerId < 0 || containerId > TrinityHostedActionPayloadCodec.MAX_CONTAINER_ID || hostId == null ||
                menuSessionId == null || layoutRevision < 0L) {
            throw new IllegalArgumentException("Invalid Trinity hosted pattern quick-move envelope");
        }
        new TrinityHostedActionTicket(TrinityDataCoreHostUiKeys.PATTERN, generation, actionSequence);
        globalSlots = TrinityHostedActionPayloadCodec.requirePatternQuickMoveSlots(globalSlots);
    }

    private TrinityHostedPatternQuickMovePayload(RegistryFriendlyByteBuf buffer) {
        this(
                TrinityHostedActionPayloadCodec.readContainerId(buffer),
                TrinityHostedActionPayloadCodec.readUuid(buffer, "host id"),
                TrinityHostedActionPayloadCodec.readUuid(buffer, "menu session id"),
                TrinityHostedActionPayloadCodec.readGeneration(buffer),
                TrinityHostedActionPayloadCodec.readSequence(buffer),
                buffer.readVarLong(),
                TrinityHostedActionPayloadCodec.readPatternQuickMoveSlots(buffer));
        TrinityHostedActionPayloadCodec.requireFullyConsumed(buffer);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        TrinityHostedActionPayloadCodec.writeContainerId(buffer, this.containerId);
        TrinityHostedActionPayloadCodec.writeUuid(buffer, this.hostId, "host id");
        TrinityHostedActionPayloadCodec.writeUuid(buffer, this.menuSessionId, "menu session id");
        TrinityHostedActionPayloadCodec.writeTicket(buffer, this.generation, this.actionSequence);
        buffer.writeVarLong(this.layoutRevision);
        TrinityHostedActionPayloadCodec.writePatternQuickMoveSlots(buffer, this.globalSlots);
    }

    public TrinityHostedActionTicket ticket() {
        return new TrinityHostedActionTicket(
                TrinityDataCoreHostUiKeys.PATTERN,
                this.generation,
                this.actionSequence);
    }

    @Override
    public Type<TrinityHostedPatternQuickMovePayload> type() {
        return TYPE;
    }

    public static void handle(TrinityHostedPatternQuickMovePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TrinityHostedActionPayloadHandler.handlePatternQuickMove(payload, context.player()));
    }
}
