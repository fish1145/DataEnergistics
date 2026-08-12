package com.fish_dan_.data_energistics.network.trinity;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.trinity.host.TrinityHostedActionTicket;
import com.fish_dan_.data_energistics.gui.ldlib2.trinity.core.TrinityDataCoreHostUiKeys;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** C2S request to migrate the current AE-grid pattern snapshot from one exact aggregate window generation. */
public record TrinityHostedPatternMigrationPayload(int containerId,
                                                   UUID hostId,
                                                   UUID menuSessionId,
                                                   long generation,
                                                   long actionSequence)
        implements CustomPacketPayload {

    public static final Type<TrinityHostedPatternMigrationPayload> TYPE = new Type<>(
            Data_Energistics.id("trinity_hosted_pattern_migration"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TrinityHostedPatternMigrationPayload> STREAM_CODEC = CustomPacketPayload.codec(TrinityHostedPatternMigrationPayload::write, TrinityHostedPatternMigrationPayload::new);

    public TrinityHostedPatternMigrationPayload {
        if (containerId < 0 || containerId > TrinityHostedActionPayloadCodec.MAX_CONTAINER_ID || hostId == null ||
                menuSessionId == null) {
            throw new IllegalArgumentException("Invalid Trinity hosted pattern-migration envelope");
        }
        new TrinityHostedActionTicket(TrinityDataCoreHostUiKeys.PATTERN, generation, actionSequence);
    }

    private TrinityHostedPatternMigrationPayload(RegistryFriendlyByteBuf buffer) {
        this(
                TrinityHostedActionPayloadCodec.readContainerId(buffer),
                TrinityHostedActionPayloadCodec.readUuid(buffer, "host id"),
                TrinityHostedActionPayloadCodec.readUuid(buffer, "menu session id"),
                TrinityHostedActionPayloadCodec.readGeneration(buffer),
                TrinityHostedActionPayloadCodec.readSequence(buffer));
        TrinityHostedActionPayloadCodec.requireFullyConsumed(buffer);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        TrinityHostedActionPayloadCodec.writeContainerId(buffer, this.containerId);
        TrinityHostedActionPayloadCodec.writeUuid(buffer, this.hostId, "host id");
        TrinityHostedActionPayloadCodec.writeUuid(buffer, this.menuSessionId, "menu session id");
        TrinityHostedActionPayloadCodec.writeTicket(buffer, this.generation, this.actionSequence);
    }

    public TrinityHostedActionTicket ticket() {
        return new TrinityHostedActionTicket(
                TrinityDataCoreHostUiKeys.PATTERN,
                this.generation,
                this.actionSequence);
    }

    @Override
    public Type<TrinityHostedPatternMigrationPayload> type() {
        return TYPE;
    }

    public static void handle(TrinityHostedPatternMigrationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TrinityHostedActionPayloadHandler.handlePatternMigration(payload, context.player()));
    }
}
