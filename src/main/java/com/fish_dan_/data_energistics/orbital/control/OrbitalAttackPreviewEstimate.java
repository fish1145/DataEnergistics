package com.fish_dan_.data_energistics.orbital.control;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackCost;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalAttackMode;
import com.fish_dan_.data_energistics.orbital.attack.OrbitalDirectedEnergyDepth;
import com.fish_dan_.data_energistics.orbital.control.session.OrbitalAttackPreviewCalculation;
import com.fish_dan_.data_energistics.orbital.reserve.OrbitalEnergyReserve;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.Nullable;

/**
 * Server-authoritative cost and workload estimate captured with one attack preview.
 *
 * <p>
 * The execution time is a best-case lower bound. Asynchronous chunk generation and contention for the shared
 * terrain budget can extend it. The unloaded count is an upper bound for newly generated chunks because an existing
 * on-disk chunk may currently be unloaded; calculating that distinction would require blocking chunk-storage I/O.
 * </p>
 */
public record OrbitalAttackPreviewEstimate(
                                           OrbitalAttackCost cost,
                                           long availableCelestialEnergy,
                                           long availableAeEnergy,
                                           long scheduledCoordinates,
                                           long scheduledBlocks,
                                           int effectRadius,
                                           int affectedChunks,
                                           int unloadedChunks,
                                           long minimumExecutionTicks) {

    private static final int SYNCHRONOUS_CAPTURE_CHUNK_BUDGET = 4_096;

    public static final Codec<OrbitalAttackPreviewEstimate> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    OrbitalAttackCost.CODEC.fieldOf("cost").forGetter(OrbitalAttackPreviewEstimate::cost),
                    Codec.LONG.fieldOf("available_celestial_energy").forGetter(OrbitalAttackPreviewEstimate::availableCelestialEnergy),
                    Codec.LONG.fieldOf("available_ae_energy").forGetter(OrbitalAttackPreviewEstimate::availableAeEnergy),
                    Codec.LONG.fieldOf("scheduled_coordinates").forGetter(OrbitalAttackPreviewEstimate::scheduledCoordinates),
                    Codec.LONG.fieldOf("scheduled_blocks").forGetter(OrbitalAttackPreviewEstimate::scheduledBlocks),
                    Codec.INT.fieldOf("effect_radius").forGetter(OrbitalAttackPreviewEstimate::effectRadius),
                    Codec.INT.fieldOf("affected_chunks").forGetter(OrbitalAttackPreviewEstimate::affectedChunks),
                    Codec.INT.fieldOf("unloaded_chunks").forGetter(OrbitalAttackPreviewEstimate::unloadedChunks),
                    Codec.LONG.fieldOf("minimum_execution_ticks").forGetter(OrbitalAttackPreviewEstimate::minimumExecutionTicks))
            .apply(instance, OrbitalAttackPreviewEstimate::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalAttackPreviewEstimate> STREAM_CODEC = StreamCodec.of(
            OrbitalAttackPreviewEstimate::encode,
            OrbitalAttackPreviewEstimate::decode);

    public OrbitalAttackPreviewEstimate {
        if (availableCelestialEnergy < 0L || availableAeEnergy < 0L || scheduledCoordinates < 0L || scheduledBlocks < 0L || effectRadius < 1 || affectedChunks < 1 || unloadedChunks < 0 || unloadedChunks > affectedChunks || minimumExecutionTicks < 1L) {
            throw new IllegalArgumentException("Invalid orbital attack preview estimate");
        }
    }

    /** Captures a complete estimate from the same immutable configuration snapshot used by the preview revision. */
    public static OrbitalAttackPreviewEstimate capture(
                                                       DataEnergisticsConfiguration configuration,
                                                       ServerLevel level,
                                                       BlockPos target,
                                                       OrbitalAttackMode mode,
                                                       int directedRadius,
                                                       @Nullable OrbitalDirectedEnergyDepth directedDepth,
                                                       OrbitalEnergyReserve reserve) {
        OrbitalAttackPreviewCalculation calculation = OrbitalAttackPreviewCalculation.begin(
                configuration,
                level,
                target,
                mode,
                directedRadius,
                directedDepth,
                reserve);
        while (!calculation.complete()) {
            calculation.advance(level, SYNCHRONOUS_CAPTURE_CHUNK_BUDGET);
        }
        return calculation.finish();
    }

    /** Returns whether the reserve snapshot shown in this preview can cover both independent escrow resources. */
    public boolean affordable() {
        return this.availableCelestialEnergy >= this.cost.celestialEnergy() && this.availableAeEnergy >= this.cost.aeEnergy();
    }

    private static void encode(RegistryFriendlyByteBuf buffer, OrbitalAttackPreviewEstimate estimate) {
        OrbitalAttackCost.STREAM_CODEC.encode(buffer, estimate.cost);
        buffer.writeVarLong(estimate.availableCelestialEnergy);
        buffer.writeVarLong(estimate.availableAeEnergy);
        buffer.writeVarLong(estimate.scheduledCoordinates);
        buffer.writeVarLong(estimate.scheduledBlocks);
        buffer.writeVarInt(estimate.effectRadius);
        buffer.writeVarInt(estimate.affectedChunks);
        buffer.writeVarInt(estimate.unloadedChunks);
        buffer.writeVarLong(estimate.minimumExecutionTicks);
    }

    private static OrbitalAttackPreviewEstimate decode(RegistryFriendlyByteBuf buffer) {
        return new OrbitalAttackPreviewEstimate(
                OrbitalAttackCost.STREAM_CODEC.decode(buffer),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarLong());
    }
}
