package com.fish_dan_.data_energistics.network.orbital.visual;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.render.orbital.OrbitalAttackVisualClientState;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackPhase;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackVisualSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Public render baseline for one dimension; it never carries ownership, reserve or authorization data. */
public record OrbitalAttackVisualsPayload(
                                           long revision,
                                           ResourceLocation dimensionId,
                                           int batchIndex,
                                           int batchCount,
                                           int totalCount,
                                           List<OrbitalAttackVisualSnapshot> attacks) implements CustomPacketPayload {

    public static final int MAX_ATTACKS = 64;
    public static final Type<OrbitalAttackVisualsPayload> TYPE = new Type<>(
            Data_Energistics.id("orbital_attack_visuals"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalAttackVisualsPayload> STREAM_CODEC = CustomPacketPayload.codec(
            OrbitalAttackVisualsPayload::write,
            OrbitalAttackVisualsPayload::new);

    public OrbitalAttackVisualsPayload {
        attacks = List.copyOf(attacks);
        if (revision < 0L || batchCount <= 0 || batchIndex < 0 || batchIndex >= batchCount
                || totalCount < 0 || attacks.size() > MAX_ATTACKS) {
            throw new IllegalArgumentException("Orbital visual baseline exceeds its bounded attack count");
        }
        int minimumBatchCount = totalCount == 0 ? 1 : ((totalCount - 1) / MAX_ATTACKS) + 1;
        if (batchCount < minimumBatchCount || batchCount > Math.max(1, totalCount)) {
            throw new IllegalArgumentException("Orbital visual batch metadata cannot represent its total count");
        }
        if (totalCount == 0 && !attacks.isEmpty()) {
            throw new IllegalArgumentException("An empty orbital visual baseline cannot contain attacks");
        }
        if (totalCount > 0 && attacks.isEmpty()) {
            throw new IllegalArgumentException("A non-empty orbital visual baseline cannot contain an empty batch");
        }
        if (attacks.stream().anyMatch(attack -> !attack.dimensionId().equals(dimensionId))) {
            throw new IllegalArgumentException("Orbital visual baseline mixes dimensions");
        }
    }

    /** Splits a complete dimension baseline into protocol-sized batches. */
    public static List<OrbitalAttackVisualsPayload> batches(
                                                            long revision,
                                                            ResourceLocation dimensionId,
                                                            List<OrbitalAttackVisualSnapshot> attacks) {
        List<OrbitalAttackVisualSnapshot> immutable = List.copyOf(attacks);
        int totalCount = immutable.size();
        int batchCount = totalCount == 0 ? 1 : ((totalCount - 1) / MAX_ATTACKS) + 1;
        ArrayList<OrbitalAttackVisualsPayload> batches = new ArrayList<>(batchCount);
        for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
            int from = batchIndex * MAX_ATTACKS;
            int to = Math.min(totalCount, from + MAX_ATTACKS);
            batches.add(new OrbitalAttackVisualsPayload(
                    revision,
                    dimensionId,
                    batchIndex,
                    batchCount,
                    totalCount,
                    immutable.subList(from, to)));
        }
        return List.copyOf(batches);
    }

    private OrbitalAttackVisualsPayload(RegistryFriendlyByteBuf buffer) {
        this(readPayload(buffer));
    }

    private OrbitalAttackVisualsPayload(Decoded decoded) {
        this(
                decoded.revision(),
                decoded.dimensionId(),
                decoded.batchIndex(),
                decoded.batchCount(),
                decoded.totalCount(),
                decoded.attacks());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarLong(this.revision);
        buffer.writeResourceLocation(this.dimensionId);
        buffer.writeVarInt(this.batchIndex);
        buffer.writeVarInt(this.batchCount);
        buffer.writeVarInt(this.totalCount);
        buffer.writeVarInt(this.attacks.size());
        for (OrbitalAttackVisualSnapshot attack : this.attacks) {
            buffer.writeUUID(attack.attackId());
            buffer.writeVarInt(attack.mode().ordinal());
            buffer.writeVarInt(attack.phase().ordinal());
            BlockPos.STREAM_CODEC.encode(buffer, attack.target());
            buffer.writeVarLong(attack.phaseAge());
            buffer.writeVarLong(attack.randomSeed());
            buffer.writeVarLong(attack.workCursor());
            buffer.writeVarLong(attack.totalWork());
        }
    }

    @Override
    public Type<OrbitalAttackVisualsPayload> type() {
        return TYPE;
    }

    public static void handle(OrbitalAttackVisualsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> OrbitalAttackVisualClientState.receive(payload));
    }

    private static Decoded readPayload(RegistryFriendlyByteBuf buffer) {
        long revision = buffer.readVarLong();
        ResourceLocation dimensionId = buffer.readResourceLocation();
        int batchIndex = buffer.readVarInt();
        int batchCount = buffer.readVarInt();
        int totalCount = buffer.readVarInt();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ATTACKS) {
            throw new IllegalArgumentException("Orbital visual attack count exceeds " + MAX_ATTACKS);
        }
        ArrayList<OrbitalAttackVisualSnapshot> attacks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            UUID attackId = buffer.readUUID();
            int modeOrdinal = buffer.readVarInt();
            int phaseOrdinal = buffer.readVarInt();
            if (modeOrdinal < 0 || modeOrdinal >= OrbitalAttackMode.values().length
                    || phaseOrdinal < 0 || phaseOrdinal >= OrbitalAttackPhase.values().length) {
                throw new IllegalArgumentException("Orbital visual enum ordinal is invalid");
            }
            OrbitalAttackMode mode = OrbitalAttackMode.values()[modeOrdinal];
            OrbitalAttackPhase phase = OrbitalAttackPhase.values()[phaseOrdinal];
            attacks.add(new OrbitalAttackVisualSnapshot(
                    attackId,
                    mode,
                    dimensionId,
                    BlockPos.STREAM_CODEC.decode(buffer),
                    phase,
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    buffer.readVarLong()));
        }
        return new Decoded(revision, dimensionId, batchIndex, batchCount, totalCount, List.copyOf(attacks));
    }

    private record Decoded(
                           long revision,
                           ResourceLocation dimensionId,
                           int batchIndex,
                           int batchCount,
                           int totalCount,
                           List<OrbitalAttackVisualSnapshot> attacks) {}
}
