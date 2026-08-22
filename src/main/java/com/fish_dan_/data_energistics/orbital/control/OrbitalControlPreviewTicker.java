package com.fish_dan_.data_energistics.orbital.control;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Expires uncommitted fire-control previews on the authoritative server clock.
 *
 * <p>
 * The UI normally removes a session on mouse release or the stop action. This ticker closes the other lifecycle
 * edge—disconnects, closed menus and abandoned sessions—without retaining one preview per player indefinitely.
 * </p>
 */
public final class OrbitalControlPreviewTicker {

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        OrbitalControlActionDispatcher.expirePreviews(event.getServer());
        OrbitalControlRequestAdmission.expire(event.getServer());
        OrbitalOwnershipActionDispatcher.expire(event.getServer());
    }
}
