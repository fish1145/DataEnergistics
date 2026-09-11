package com.fish_dan_.data_energistics.orbital.reserve;

import com.fish_dan_.data_energistics.ae2.key.StellarFluxKey;
import com.fish_dan_.data_energistics.blockentity.orbital.OrbitalEndpointBlockEntity;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointAvailability;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointRecord;
import com.fish_dan_.data_energistics.orbital.model.StellarErasureDeviceRecord;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;

import net.minecraft.server.MinecraftServer;

import java.util.Comparator;
import java.util.Optional;

/**
 * Charges one weapon from one priority-selected, operational AE endpoint per server tick.
 */
public final class OrbitalReserveCharging {

    private static final Comparator<OrbitalEndpointRecord> ENDPOINT_ORDER = Comparator
            .comparingInt(OrbitalEndpointRecord::priority)
            .thenComparing(endpoint -> endpoint.location().dimensionId().toString())
            .thenComparingInt(endpoint -> endpoint.location().pos().getX())
            .thenComparingInt(endpoint -> endpoint.location().pos().getY())
            .thenComparingInt(endpoint -> endpoint.location().pos().getZ());

    private OrbitalReserveCharging() {}

    /**
     * Returns the weapon state after at most one endpoint has attempted a reserve transfer.
     *
     * <p>
     * Endpoints are tried in owner-controlled priority order. Failover occurs only when a grid cannot provide any
     * currently needed resource. Once a grid can provide either resource, both extractions are confined to that grid
     * for this tick, even if the other resource remains unavailable.
     * </p>
     */
    public static StellarErasureDeviceRecord charge(
                                             MinecraftServer server,
                                             StellarErasureDeviceRecord weapon,
                                             DataEnergisticsConfiguration.StellarErasureDeviceSchema settings) {
        OrbitalEnergyReserve reserve = weapon.reserve().withinCapacity(settings);
        long stellarFluxRequest = Math.min(
                reserve.stellarFluxSpace(settings),
                settings.stellarFluxChargePerTick);
        long aeEnergyRequest = Math.min(
                reserve.aeEnergySpace(settings),
                settings.aeEnergyChargePerTick);
        if (stellarFluxRequest == 0L && aeEnergyRequest == 0L) {
            return weapon.withReserve(reserve);
        }

        for (OrbitalEndpointRecord endpoint : weapon.endpoints().values().stream().sorted(ENDPOINT_ORDER).toList()) {
            Optional<OrbitalEndpointBlockEntity> blockEntity = OrbitalEndpointAvailability.findOperationalBlockEntity(
                    server,
                    weapon.weaponId(),
                    endpoint);
            if (blockEntity.isEmpty()) {
                continue;
            }
            Optional<OrbitalEnergyReserve> transferred = transferFromGrid(
                    blockEntity.orElseThrow(),
                    reserve,
                    stellarFluxRequest,
                    aeEnergyRequest,
                    settings);
            if (transferred.isPresent()) {
                return weapon.withReserve(transferred.orElseThrow());
            }
        }
        return weapon.withReserve(reserve);
    }

    private static Optional<OrbitalEnergyReserve> transferFromGrid(
                                                                   OrbitalEndpointBlockEntity endpoint,
                                                                   OrbitalEnergyReserve reserve,
                                                                   long stellarFluxRequest,
                                                                   long aeEnergyRequest,
                                                                   DataEnergisticsConfiguration.StellarErasureDeviceSchema settings) {
        IGrid grid = endpoint.getMainNode().getGrid();
        IActionSource actionSource = IActionSource.ofMachine(endpoint);
        long availableStellarFlux = stellarFluxRequest == 0L ? 0L : grid.getStorageService()
                .getInventory()
                .extract(StellarFluxKey.of(), stellarFluxRequest, Actionable.SIMULATE, actionSource);
        long availableAeEnergy = aeEnergyRequest == 0L ? 0L : wholeAeEnergy(
                grid.getEnergyService().extractAEPower(
                        aeEnergyRequest,
                        Actionable.SIMULATE,
                        PowerMultiplier.ONE),
                aeEnergyRequest);
        if (availableStellarFlux == 0L && availableAeEnergy == 0L) {
            return Optional.empty();
        }

        long transferredStellarFlux = availableStellarFlux == 0L ? 0L : grid.getStorageService()
                .getInventory()
                .extract(StellarFluxKey.of(), availableStellarFlux, Actionable.MODULATE, actionSource);
        long transferredAeEnergy = availableAeEnergy == 0L ? 0L : wholeAeEnergy(
                grid.getEnergyService().extractAEPower(
                        availableAeEnergy,
                        Actionable.MODULATE,
                        PowerMultiplier.ONE),
                availableAeEnergy);
        return Optional.of(reserve.withTransfer(
                transferredStellarFlux,
                transferredAeEnergy,
                settings));
    }

    private static long wholeAeEnergy(double extracted, long requested) {
        if (!Double.isFinite(extracted) || extracted < 0.0D) {
            throw new IllegalStateException("AE grid returned an invalid extracted energy amount: " + extracted);
        }
        return Math.min(requested, (long) Math.floor(extracted));
    }
}
