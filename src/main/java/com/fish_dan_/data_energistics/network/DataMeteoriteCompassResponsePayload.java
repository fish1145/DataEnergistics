package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public record DataMeteoriteCompassResponsePayload(ChunkPos requestedPos,
                                                  Optional<BlockPos> closestMeteorite)
        implements CustomPacketPayload {

    public static final Type<DataMeteoriteCompassResponsePayload> TYPE = new Type<>(Data_Energistics.id("data_meteorite_compass_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DataMeteoriteCompassResponsePayload> STREAM_CODEC = CustomPacketPayload.codec(
            DataMeteoriteCompassResponsePayload::write,
            DataMeteoriteCompassResponsePayload::new);

    private DataMeteoriteCompassResponsePayload(RegistryFriendlyByteBuf buf) {
        this(new ChunkPos(buf.readVarInt(), buf.readVarInt()),
                ByteBufCodecs.optional(BlockPos.STREAM_CODEC).decode(buf));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.requestedPos.x);
        buf.writeVarInt(this.requestedPos.z);
        ByteBufCodecs.optional(BlockPos.STREAM_CODEC).encode(buf, this.closestMeteorite);
    }

    @Override
    public Type<DataMeteoriteCompassResponsePayload> type() {
        return TYPE;
    }

    public static void handle(DataMeteoriteCompassResponsePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DataMeteoriteCompassResponseHandler.cacheSyncedCompassResult(payload));
    }
}
