package com.fish_dan_.data_energistics.orbital.attack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Advances persisted orbital attack work after reserve charging has completed for the server tick.
 */
public final class OrbitalAttackTicker {

    public OrbitalAttackTicker() {}

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        OrbitalAttackSavedData.get(event.getServer()).tick(event.getServer());
    }
}
