package com.fish_dan_.data_energistics.client.hud.orbital;

import com.fish_dan_.data_energistics.network.orbital.control.OrbitalControlHudSnapshotPayload;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlTerminalSnapshot.WeaponEntry;
import com.fish_dan_.data_energistics.orbital.control.protocol.OrbitalHudSnapshot;

import org.jspecify.annotations.Nullable;

/** Client cache for the latest server-authoritative orbital HUD snapshot. */
public final class OrbitalControlHudClientState {

    private static long revision = -1L;
    private static boolean visible;
    private static boolean userEnabled = true;
    private static OrbitalHudSnapshot snapshot = OrbitalHudSnapshot.EMPTY;

    private OrbitalControlHudClientState() {}

    public static void receive(OrbitalControlHudSnapshotPayload payload) {
        if (payload.revision() < revision) {
            return;
        }
        revision = payload.revision();
        visible = payload.visible();
        snapshot = payload.snapshot();
    }

    /** Clears the server-scoped HUD baseline when the client leaves a server. */
    public static void clear() {
        revision = -1L;
        visible = false;
        snapshot = OrbitalHudSnapshot.EMPTY;
    }

    public static boolean visible() {
        return visible && userEnabled;
    }

    @Nullable
    public static WeaponEntry weapon() {
        return visible() ? snapshot.weapon() : null;
    }

    public static OrbitalHudSnapshot snapshot() {
        return snapshot;
    }

    public static void toggleUserEnabled() {
        userEnabled = !userEnabled;
    }
}
