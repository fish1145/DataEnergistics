package com.fish_dan_.data_energistics.orbital.provisioning;

import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointKind;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.model.OrbitalWeaponRecord;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.server.MinecraftServer;

import java.util.UUID;

/**
 * Initial provisioning path used by a placed orbital control console.
 */
public final class ConsoleWeaponProvisioner implements OrbitalWeaponProvisioner {

    public static final ConsoleWeaponProvisioner INSTANCE = new ConsoleWeaponProvisioner();

    private ConsoleWeaponProvisioner() {}

    @Override
    public OrbitalWeaponRecord provision(
                                         MinecraftServer server,
                                         UUID ownerId,
                                         OrbitalEndpointLocation sourceLocation) {
        return OrbitalWeaponSavedData.get(server)
                .provisionForOwner(
                        server,
                        ownerId,
                        sourceLocation,
                        OrbitalEndpointKind.CONTROL_CONSOLE);
    }
}
