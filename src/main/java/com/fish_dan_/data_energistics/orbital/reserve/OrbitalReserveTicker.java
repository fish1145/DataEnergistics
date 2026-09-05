package com.fish_dan_.data_energistics.orbital.reserve;

import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Connects persistent orbital reserve charging to the server tick lifecycle.
 */
public final class OrbitalReserveTicker {

    public OrbitalReserveTicker() {}

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        OrbitalWeaponSavedData.get(event.getServer()).chargeReserves(event.getServer());
    }
}
