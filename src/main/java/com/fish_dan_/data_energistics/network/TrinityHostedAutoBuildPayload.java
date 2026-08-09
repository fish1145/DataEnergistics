package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.autobuild.TrinityAutoBuildSubmission;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionTicket;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.TrinityDataCoreHostUiKeys;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** C2S revision-bound auto-build action from one exact host and menu session. */
public record TrinityHostedAutoBuildPayload(int containerId,
                                            UUID hostId,
                                            UUID menuSessionId,
                                            long generation,
                                            long actionSequence,
                                            TrinityAutoBuildSubmission submission)
        implements CustomPacketPayload {

    public static final Type<TrinityHostedAutoBuildPayload> TYPE = new Type<>(
            Data_Energistics.id("trinity_hosted_auto_build"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityHostedAutoBuildPayload> STREAM_CODEC = CustomPacketPayload.codec(TrinityHostedAutoBuildPayload::write, TrinityHostedAutoBuildPayload::new);

    /** Rejects invalid envelope and ticket values before transport. */
    public TrinityHostedAutoBuildPayload {
        if (containerId < 0 || containerId > TrinityHostedActionPayloadCodec.MAX_CONTAINER_ID || hostId == null ||
                menuSessionId == null) {
            throw new IllegalArgumentException("Invalid Trinity hosted auto-build envelope");
        }
        new TrinityHostedActionTicket(TrinityDataCoreHostUiKeys.AUTO_BUILD, generation, actionSequence);
        if (submission == null) {
            throw new IllegalArgumentException("Trinity hosted auto-build submission cannot be null");
        }
    }

    private TrinityHostedAutoBuildPayload(RegistryFriendlyByteBuf buffer) {
        this(
                TrinityHostedActionPayloadCodec.readContainerId(buffer),
                TrinityHostedActionPayloadCodec.readUuid(buffer, "host id"),
                TrinityHostedActionPayloadCodec.readUuid(buffer, "menu session id"),
                TrinityHostedActionPayloadCodec.readGeneration(buffer),
                TrinityHostedActionPayloadCodec.readSequence(buffer),
                TrinityHostedActionPayloadCodec.readSubmission(buffer));
        TrinityHostedActionPayloadCodec.requireFullyConsumed(buffer);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        TrinityHostedActionPayloadCodec.writeContainerId(buffer, this.containerId);
        TrinityHostedActionPayloadCodec.writeUuid(buffer, this.hostId, "host id");
        TrinityHostedActionPayloadCodec.writeUuid(buffer, this.menuSessionId, "menu session id");
        TrinityHostedActionPayloadCodec.writeTicket(buffer, this.generation, this.actionSequence);
        TrinityHostedActionPayloadCodec.writeSubmission(buffer, this.submission);
    }

    /** Returns the fixed automatic-build window identity carried by this request. */
    public TrinityHostedActionTicket ticket() {
        return new TrinityHostedActionTicket(
                TrinityDataCoreHostUiKeys.AUTO_BUILD,
                this.generation,
                this.actionSequence);
    }

    @Override
    public Type<TrinityHostedAutoBuildPayload> type() {
        return TYPE;
    }

    /** Defers authoritative validation and execution to the server main thread. */
    public static void handle(TrinityHostedAutoBuildPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TrinityHostedActionPayloadHandler.handleAutoBuild(payload, context.player()));
    }
}
