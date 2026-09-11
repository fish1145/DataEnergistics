package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Immutable resource escrow and cooldown captured when an attack is confirmed.
 */
public record OrbitalAttackCost(
                                long stellarFlux,
                                long aeEnergy,
                                int cooldownTicks) {

    public static final Codec<OrbitalAttackCost> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.LONG.fieldOf("stellar_flux").forGetter(OrbitalAttackCost::stellarFlux),
                    Codec.LONG.fieldOf("ae_energy").forGetter(OrbitalAttackCost::aeEnergy),
                    Codec.INT.fieldOf("cooldown_ticks").forGetter(OrbitalAttackCost::cooldownTicks))
            .apply(instance, OrbitalAttackCost::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, OrbitalAttackCost> STREAM_CODEC = StreamCodec.of(
            (buffer, cost) -> {
                buffer.writeVarLong(cost.stellarFlux);
                buffer.writeVarLong(cost.aeEnergy);
                buffer.writeVarInt(cost.cooldownTicks);
            },
            buffer -> new OrbitalAttackCost(
                    buffer.readVarLong(),
                    buffer.readVarLong(),
                    buffer.readVarInt()));

    public OrbitalAttackCost {
        if (stellarFlux <= 0L || aeEnergy <= 0L || cooldownTicks < 0) {
            throw new IllegalArgumentException("Attack costs must be positive and cooldown must not be negative");
        }
    }

    /**
     * Reads the kinetic cost from one immutable configuration snapshot.
     */
    public static OrbitalAttackCost kinetic(DataEnergisticsConfiguration.StellarErasureDeviceSchema settings) {
        return new OrbitalAttackCost(
                settings.kineticStellarFluxCost,
                settings.kineticAeEnergyCost,
                settings.kineticCooldownTicks);
    }

    /**
     * Calculates the complete directed-energy escrow, including every scheduled disk coordinate.
     */
    public static OrbitalAttackCost directedEnergy(
                                                   DataEnergisticsConfiguration.StellarErasureDeviceSchema settings,
                                                   long scheduledCoordinates) {
        if (scheduledCoordinates <= 0L) {
            throw new IllegalArgumentException("A directed-energy scan must schedule at least one coordinate");
        }
        long celestial = Math.addExact(
                settings.directedEnergyBaseStellarFluxCost,
                Math.multiplyExact(settings.directedEnergyStellarFluxPerCoordinate, scheduledCoordinates));
        long ae = Math.addExact(
                settings.directedEnergyBaseAeEnergyCost,
                Math.multiplyExact(settings.directedEnergyAeEnergyPerCoordinate, scheduledCoordinates));
        return new OrbitalAttackCost(celestial, ae, settings.directedEnergyCooldownTicks);
    }

    /**
     * Reads the fixed digital-annihilation payload cost from one immutable configuration snapshot.
     */
    public static OrbitalAttackCost digitalAnnihilation(DataEnergisticsConfiguration.StellarErasureDeviceSchema settings) {
        return new OrbitalAttackCost(
                settings.digitalAnnihilationStellarFluxCost,
                settings.digitalAnnihilationAeEnergyCost,
                settings.digitalAnnihilationCooldownTicks);
    }
}
