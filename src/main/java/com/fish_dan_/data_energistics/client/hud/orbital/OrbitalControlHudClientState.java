package com.fish_dan_.data_energistics.client.hud.orbital;

import com.fish_dan_.data_energistics.network.orbital.control.OrbitalControlHudSnapshotPayload;

import net.minecraft.network.chat.Component;

import org.jspecify.annotations.Nullable;

/** Client cache for the latest server-authoritative orbital HUD snapshot. */
public final class OrbitalControlHudClientState {

    private static long revision = -1L;
    private static boolean visible;
    private static Component status = Component.empty();

    private OrbitalControlHudClientState() {}

    public static void receive(OrbitalControlHudSnapshotPayload payload) {
        if (payload.revision() < revision) {
            return;
        }
        revision = payload.revision();
        visible = payload.visible();
        status = payload.status();
    }

    /** Clears the server-scoped HUD baseline when the client leaves a server. */
    public static void clear() {
        revision = -1L;
        visible = false;
        status = Component.empty();
    }

    public static boolean visible() {
        return visible;
    }

    @Nullable
    public static Component status() {
        return visible ? status : null;
    }
}
