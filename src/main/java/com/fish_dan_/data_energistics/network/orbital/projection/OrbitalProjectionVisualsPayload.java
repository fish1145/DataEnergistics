package com.fish_dan_.data_energistics.network.orbital.projection;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.orbital.OrbitalProjectionVisualClientState;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponLifecycleState;
import com.fish_dan_.data_energistics.orbital.projection.OrbitalProjectionVisualSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Public primary-projection baseline for one dimension; no owner, reserve or authorization fields are transmitted. */
public record OrbitalProjectionVisualsPayload(
                                               long revision,
                                               ResourceLocation dimensionId,
                                               int batchIndex,
                                               int batchCount,
                                               int totalCount,
                                               List<OrbitalProjectionVisualSnapshot> projections)
        implements CustomPacketPayload {

    public static final int MAX_PROJECTIONS = 64;
    public static final Type<OrbitalProjectionVisualsPayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_projection_visuals"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalProjectionVisualsPayload> STREAM_CODEC = CustomPacketPayload.codec(
            OrbitalProjectionVisualsPayload::write,
            OrbitalProjectionVisualsPayload::new);

    public OrbitalProjectionVisualsPayload {
        projections = List.copyOf(projections);
        if (revision < 0L || batchCount <= 0 || batchIndex < 0 || batchIndex >= batchCount
                || totalCount < 0 || projections.size() > MAX_PROJECTIONS) {
            throw new IllegalArgumentException("Orbital projection batch metadata is outside its bounded range");
        }
        int minimumBatchCount = totalCount == 0 ? 1 : ((totalCount - 1) / MAX_PROJECTIONS) + 1;
        if (batchCount < minimumBatchCount || batchCount > Math.max(1, totalCount)) {
            throw new IllegalArgumentException("Orbital projection batch metadata cannot represent its total count");
        }
        if (totalCount == 0 && !projections.isEmpty()) {
            throw new IllegalArgumentException("An empty orbital projection baseline cannot contain projections");
        }
        if (totalCount > 0 && projections.isEmpty()) {
            throw new IllegalArgumentException("A non-empty orbital projection baseline cannot contain an empty batch");
        }
        if (projections.stream().anyMatch(projection -> !projection.dimensionId().equals(dimensionId))) {
            throw new IllegalArgumentException("Orbital projection baseline mixes dimensions");
        }
    }

    /** Splits a complete dimension baseline into protocol-sized batches. */
    public static List<OrbitalProjectionVisualsPayload> batches(
                                                                 long revision,
                                                                 ResourceLocation dimensionId,
                                                                 List<OrbitalProjectionVisualSnapshot> projections) {
        List<OrbitalProjectionVisualSnapshot> immutable = List.copyOf(projections);
        int totalCount = immutable.size();
        int batchCount = totalCount == 0 ? 1 : ((totalCount - 1) / MAX_PROJECTIONS) + 1;
        ArrayList<OrbitalProjectionVisualsPayload> batches = new ArrayList<>(batchCount);
        for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
            int from = batchIndex * MAX_PROJECTIONS;
            int to = Math.min(totalCount, from + MAX_PROJECTIONS);
            batches.add(new OrbitalProjectionVisualsPayload(
                    revision,
                    dimensionId,
                    batchIndex,
                    batchCount,
                    totalCount,
                    immutable.subList(from, to)));
        }
        return List.copyOf(batches);
    }

    private OrbitalProjectionVisualsPayload(RegistryFriendlyByteBuf buffer) {
        this(readPayload(buffer));
    }

    private OrbitalProjectionVisualsPayload(Decoded decoded) {
        this(
                decoded.revision(),
                decoded.dimensionId(),
                decoded.batchIndex(),
                decoded.batchCount(),
                decoded.totalCount(),
                decoded.projections());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarLong(this.revision);
        buffer.writeResourceLocation(this.dimensionId);
        buffer.writeVarInt(this.batchIndex);
        buffer.writeVarInt(this.batchCount);
        buffer.writeVarInt(this.totalCount);
        buffer.writeVarInt(this.projections.size());
        for (OrbitalProjectionVisualSnapshot projection : this.projections) {
            buffer.writeUUID(projection.weaponId());
            BlockPos.STREAM_CODEC.encode(buffer, projection.anchor());
            buffer.writeInt(projection.projectionY());
            buffer.writeVarInt(projection.lifecycleState().ordinal());
            buffer.writeVarInt(projection.redeploymentTicksRemaining());
            buffer.writeVarLong(projection.animationTime());
            buffer.writeVarLong(projection.randomSeed());
        }
    }

    @Override
    public Type<OrbitalProjectionVisualsPayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalProjectionVisualsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> OrbitalProjectionVisualClientState.receive(payload));
    }

    private static Decoded readPayload(RegistryFriendlyByteBuf buffer) {
        long revision = buffer.readVarLong();
        ResourceLocation dimensionId = buffer.readResourceLocation();
        int batchIndex = buffer.readVarInt();
        int batchCount = buffer.readVarInt();
        int totalCount = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_PROJECTIONS) {
            throw new IllegalArgumentException("Orbital projection count exceeds " + MAX_PROJECTIONS);
        }
        ArrayList<OrbitalProjectionVisualSnapshot> projections = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            UUID weaponId = buffer.readUUID();
            BlockPos anchor = BlockPos.STREAM_CODEC.decode(buffer);
            int projectionY = buffer.readInt();
            OrbitalWeaponLifecycleState lifecycleState = readLifecycle(buffer.readVarInt());
            projections.add(new OrbitalProjectionVisualSnapshot(
                    weaponId,
                    dimensionId,
                    anchor,
                    projectionY,
                    lifecycleState,
                    buffer.readVarInt(),
                    buffer.readVarLong(),
                    buffer.readVarLong()));
        }
        return new Decoded(revision, dimensionId, batchIndex, batchCount, totalCount, List.copyOf(projections));
    }

    private static OrbitalWeaponLifecycleState readLifecycle(int ordinal) {
        if (ordinal < 0 || ordinal >= OrbitalWeaponLifecycleState.values().length) {
            throw new IllegalArgumentException("Orbital projection lifecycle ordinal is invalid");
        }
        return OrbitalWeaponLifecycleState.values()[ordinal];
    }

    private record Decoded(
                           long revision,
                           ResourceLocation dimensionId,
                           int batchIndex,
                           int batchCount,
                           int totalCount,
                           List<OrbitalProjectionVisualSnapshot> projections) {}
}
