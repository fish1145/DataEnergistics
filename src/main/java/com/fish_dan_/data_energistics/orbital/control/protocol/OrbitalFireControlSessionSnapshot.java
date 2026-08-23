package com.fish_dan_.data_energistics.orbital.control.protocol;

import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.control.OrbitalAttackPreviewEstimate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Atomic server view of one menu-scoped preview calculation and confirmation hold. */
public record OrbitalFireControlSessionSnapshot(
                                                Phase phase,
                                                @Nullable PreviewDetails preview,
                                                int checkedChunks,
                                                int totalChunks,
                                                long heldTicks,
                                                long requiredHoldTicks,
                                                long serverGameTime,
                                                long expiresAt) {

    public static final OrbitalFireControlSessionSnapshot IDLE = new OrbitalFireControlSessionSnapshot(
            Phase.IDLE,
            null,
            0,
            0,
            0L,
            0L,
            0L,
            0L);
    public static final OrbitalFireControlSessionSnapshot REJECTED = new OrbitalFireControlSessionSnapshot(
            Phase.REJECTED,
            null,
            0,
            0,
            0L,
            0L,
            0L,
            0L);

    public static final Codec<OrbitalFireControlSessionSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Phase.CODEC.fieldOf("phase").forGetter(OrbitalFireControlSessionSnapshot::phase),
                    PreviewDetails.CODEC.optionalFieldOf("preview").forGetter(snapshot -> Optional.ofNullable(snapshot.preview)),
                    Codec.INT.fieldOf("checked_chunks").forGetter(OrbitalFireControlSessionSnapshot::checkedChunks),
                    Codec.INT.fieldOf("total_chunks").forGetter(OrbitalFireControlSessionSnapshot::totalChunks),
                    Codec.LONG.fieldOf("held_ticks").forGetter(OrbitalFireControlSessionSnapshot::heldTicks),
                    Codec.LONG.fieldOf("required_hold_ticks").forGetter(OrbitalFireControlSessionSnapshot::requiredHoldTicks),
                    Codec.LONG.fieldOf("server_game_time").forGetter(OrbitalFireControlSessionSnapshot::serverGameTime),
                    Codec.LONG.fieldOf("expires_at").forGetter(OrbitalFireControlSessionSnapshot::expiresAt))
            .apply(instance, (phase, preview, checkedChunks, totalChunks, heldTicks, requiredHoldTicks, serverGameTime, expiresAt) -> new OrbitalFireControlSessionSnapshot(
                    phase,
                    preview.orElse(null),
                    checkedChunks,
                    totalChunks,
                    heldTicks,
                    requiredHoldTicks,
                    serverGameTime,
                    expiresAt)));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalFireControlSessionSnapshot> STREAM_CODEC = StreamCodec.of(
            OrbitalFireControlSessionSnapshot::encode,
            OrbitalFireControlSessionSnapshot::decode);

    public OrbitalFireControlSessionSnapshot {
        if (checkedChunks < 0 || totalChunks < 0 || checkedChunks > totalChunks || heldTicks < 0L ||
                requiredHoldTicks < 0L || serverGameTime < 0L || expiresAt < 0L) {
            throw new IllegalArgumentException("Orbital fire-control progress cannot be negative or exceed its total");
        }
        switch (phase) {
            case IDLE, REJECTED -> {
                if (preview != null || checkedChunks != 0 || totalChunks != 0 || heldTicks != 0L ||
                        requiredHoldTicks != 0L || serverGameTime != 0L || expiresAt != 0L) {
                    throw new IllegalArgumentException("An inactive orbital fire-control session cannot carry preview state");
                }
            }
            case CALCULATING -> {
                if (preview == null || preview.nonce != null || preview.estimate != null || totalChunks < 1 ||
                        heldTicks != 0L || requiredHoldTicks != 0L || expiresAt != 0L) {
                    throw new IllegalArgumentException("A calculating orbital preview has invalid ready-state fields");
                }
            }
            case READY -> {
                if (preview == null || preview.nonce == null || preview.estimate == null || checkedChunks != totalChunks ||
                        totalChunks < 1 || heldTicks != 0L || requiredHoldTicks < 1L || serverGameTime < 1L ||
                        expiresAt <= serverGameTime) {
                    throw new IllegalArgumentException("A ready orbital preview is incomplete");
                }
            }
            case HOLDING -> {
                if (preview == null || preview.nonce == null || preview.estimate == null || checkedChunks != totalChunks ||
                        totalChunks < 1 || requiredHoldTicks < 1L || serverGameTime < 1L || expiresAt <= serverGameTime) {
                    throw new IllegalArgumentException("A holding orbital preview is incomplete");
                }
            }
        }
    }

    public static OrbitalFireControlSessionSnapshot calculating(
                                                                 PreviewDetails preview,
                                                                 int checkedChunks,
                                                                 int totalChunks) {
        return new OrbitalFireControlSessionSnapshot(
                Phase.CALCULATING,
                preview,
                checkedChunks,
                totalChunks,
                0L,
                0L,
                0L,
                0L);
    }

    public static OrbitalFireControlSessionSnapshot ready(
                                                           PreviewDetails preview,
                                                           long requiredHoldTicks,
                                                           long serverGameTime,
                                                           long expiresAt) {
        int chunks = Objects.requireNonNull(preview.estimate()).affectedChunks();
        return new OrbitalFireControlSessionSnapshot(
                Phase.READY,
                preview,
                chunks,
                chunks,
                0L,
                requiredHoldTicks,
                serverGameTime,
                expiresAt);
    }

    public static OrbitalFireControlSessionSnapshot holding(
                                                             PreviewDetails preview,
                                                             long heldTicks,
                                                             long requiredHoldTicks,
                                                             long serverGameTime,
                                                             long expiresAt) {
        int chunks = Objects.requireNonNull(preview.estimate()).affectedChunks();
        return new OrbitalFireControlSessionSnapshot(
                Phase.HOLDING,
                preview,
                chunks,
                chunks,
                heldTicks,
                requiredHoldTicks,
                serverGameTime,
                expiresAt);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, OrbitalFireControlSessionSnapshot snapshot) {
        buffer.writeVarInt(snapshot.phase.ordinal());
        buffer.writeBoolean(snapshot.preview != null);
        if (snapshot.preview != null) {
            PreviewDetails.STREAM_CODEC.encode(buffer, snapshot.preview);
        }
        buffer.writeVarInt(snapshot.checkedChunks);
        buffer.writeVarInt(snapshot.totalChunks);
        buffer.writeVarLong(snapshot.heldTicks);
        buffer.writeVarLong(snapshot.requiredHoldTicks);
        buffer.writeVarLong(snapshot.serverGameTime);
        buffer.writeVarLong(snapshot.expiresAt);
    }

    private static OrbitalFireControlSessionSnapshot decode(RegistryFriendlyByteBuf buffer) {
        Phase phase = Phase.fromOrdinal(buffer.readVarInt());
        PreviewDetails preview = buffer.readBoolean() ? PreviewDetails.STREAM_CODEC.decode(buffer) : null;
        return new OrbitalFireControlSessionSnapshot(
                phase,
                preview,
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarLong());
    }

    public enum Phase {

        IDLE,
        CALCULATING,
        READY,
        HOLDING,
        REJECTED;

        private static final Codec<Phase> CODEC = Codec.STRING.xmap(Phase::valueOf, Phase::name);

        private static Phase fromOrdinal(int ordinal) {
            Phase[] values = values();
            if (ordinal < 0 || ordinal >= values.length) {
                throw new IllegalArgumentException("Unknown orbital fire-control phase ordinal: " + ordinal);
            }
            return values[ordinal];
        }
    }

    /** Immutable target, geometry and completed estimate shared by READY and HOLDING states. */
    public record PreviewDetails(
                                 UUID weaponId,
                                 OrbitalAttackMode mode,
                                 ResourceLocation dimensionId,
                                 BlockPos target,
                                 int directedRadius,
                                 @Nullable OrbitalDirectedEnergyDepth directedDepth,
                                 @Nullable UUID nonce,
                                 @Nullable OrbitalAttackPreviewEstimate estimate) {

        private static final Codec<OrbitalAttackMode> MODE_CODEC = Codec.STRING.xmap(
                OrbitalAttackMode::valueOf,
                OrbitalAttackMode::name);
        private static final Codec<OrbitalDirectedEnergyDepth> DEPTH_CODEC = Codec.STRING.xmap(
                OrbitalDirectedEnergyDepth::valueOf,
                OrbitalDirectedEnergyDepth::name);

        public static final Codec<PreviewDetails> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        UUIDUtil.CODEC.fieldOf("weapon_id").forGetter(PreviewDetails::weaponId),
                        MODE_CODEC.fieldOf("mode").forGetter(PreviewDetails::mode),
                        ResourceLocation.CODEC.fieldOf("dimension_id").forGetter(PreviewDetails::dimensionId),
                        BlockPos.CODEC.fieldOf("target").forGetter(PreviewDetails::target),
                        Codec.INT.fieldOf("directed_radius").forGetter(PreviewDetails::directedRadius),
                        DEPTH_CODEC.optionalFieldOf("directed_depth").forGetter(details -> Optional.ofNullable(details.directedDepth)),
                        UUIDUtil.CODEC.optionalFieldOf("nonce").forGetter(details -> Optional.ofNullable(details.nonce)),
                        OrbitalAttackPreviewEstimate.CODEC.optionalFieldOf("estimate").forGetter(details -> Optional.ofNullable(details.estimate)))
                .apply(instance, (weaponId, mode, dimensionId, target, directedRadius, directedDepth, nonce, estimate) -> new PreviewDetails(
                        weaponId,
                        mode,
                        dimensionId,
                        target,
                        directedRadius,
                        directedDepth.orElse(null),
                        nonce.orElse(null),
                        estimate.orElse(null))));
        public static final StreamCodec<RegistryFriendlyByteBuf, PreviewDetails> STREAM_CODEC = StreamCodec.of(
                PreviewDetails::encode,
                PreviewDetails::decode);

        public PreviewDetails {
            target = target.immutable();
            if (mode == OrbitalAttackMode.DIRECTED_ENERGY) {
                if (directedRadius < 1 || directedDepth == null) {
                    throw new IllegalArgumentException("Directed-energy preview details require radius and depth");
                }
            } else if (directedRadius != 0 || directedDepth != null) {
                throw new IllegalArgumentException("Non-directed preview details cannot carry directed-energy geometry");
            }
            if ((nonce == null) != (estimate == null)) {
                throw new IllegalArgumentException("An orbital preview nonce and estimate must become available atomically");
            }
        }

        private static void encode(RegistryFriendlyByteBuf buffer, PreviewDetails details) {
            buffer.writeUUID(details.weaponId);
            buffer.writeVarInt(details.mode.wireCode());
            buffer.writeResourceLocation(details.dimensionId);
            buffer.writeBlockPos(details.target);
            buffer.writeVarInt(details.directedRadius);
            buffer.writeBoolean(details.directedDepth != null);
            if (details.directedDepth != null) {
                buffer.writeVarInt(details.directedDepth.wireCode());
            }
            buffer.writeBoolean(details.nonce != null);
            if (details.nonce != null) {
                buffer.writeUUID(details.nonce);
                OrbitalAttackPreviewEstimate.STREAM_CODEC.encode(buffer, Objects.requireNonNull(details.estimate));
            }
        }

        private static PreviewDetails decode(RegistryFriendlyByteBuf buffer) {
            UUID weaponId = buffer.readUUID();
            OrbitalAttackMode mode = OrbitalAttackMode.fromWireCode(buffer.readVarInt());
            ResourceLocation dimensionId = buffer.readResourceLocation();
            BlockPos target = buffer.readBlockPos();
            int directedRadius = buffer.readVarInt();
            OrbitalDirectedEnergyDepth directedDepth = buffer.readBoolean() ?
                    OrbitalDirectedEnergyDepth.fromWireCode(buffer.readVarInt()) : null;
            UUID nonce = null;
            OrbitalAttackPreviewEstimate estimate = null;
            if (buffer.readBoolean()) {
                nonce = buffer.readUUID();
                estimate = OrbitalAttackPreviewEstimate.STREAM_CODEC.decode(buffer);
            }
            return new PreviewDetails(
                    weaponId,
                    mode,
                    dimensionId,
                    target,
                    directedRadius,
                    directedDepth,
                    nonce,
                    estimate);
        }
    }
}
