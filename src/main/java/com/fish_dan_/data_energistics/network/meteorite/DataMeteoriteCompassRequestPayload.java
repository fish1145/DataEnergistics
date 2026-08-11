package com.fish_dan_.data_energistics.network.meteorite;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.world.DataMeteoriteSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public record DataMeteoriteCompassRequestPayload(ChunkPos requestedPos) implements CustomPacketPayload {

    public static final Type<DataMeteoriteCompassRequestPayload> TYPE = new Type<>(Data_Energistics.id("data_meteorite_compass_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DataMeteoriteCompassRequestPayload> STREAM_CODEC = CustomPacketPayload.codec(
            DataMeteoriteCompassRequestPayload::write,
            DataMeteoriteCompassRequestPayload::new);

    private DataMeteoriteCompassRequestPayload(RegistryFriendlyByteBuf buf) {
        this(new ChunkPos(buf.readVarInt(), buf.readVarInt()));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(this.requestedPos.x);
        buf.writeVarInt(this.requestedPos.z);
    }

    @Override
    public Type<DataMeteoriteCompassRequestPayload> type() {
        return TYPE;
    }

    public static void handle(DataMeteoriteCompassRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player().level() instanceof ServerLevel level)) {
                return;
            }

            Optional<BlockPos> closest = Optional.ofNullable(
                    DataMeteoriteSavedData.get(level).findClosest(payload.requestedPos()));

            PacketDistributor.sendToPlayer(
                    (ServerPlayer) context.player(),
                    new DataMeteoriteCompassResponsePayload(payload.requestedPos(), closest));
        });
    }
}
