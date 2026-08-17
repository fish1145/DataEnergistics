package com.fish_dan_.data_energistics.orbital.control;

import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.model.OrbitalAccessRole;
import com.fish_dan_.data_energistics.orbital.storage.OrbitalWeaponSavedData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Server-thread entry points for owner-controlled endpoint, anchor and delegated-access mutations.
 *
 * <p>The acting UUID always comes from the authenticated {@link ServerPlayer}. Payloads and commands may identify a
 * weapon, endpoint or delegated player, but the authoritative SavedData action matrix decides whether the mutation is
 * allowed.</p>
 */
public final class OrbitalWeaponAdministrationDispatcher {

    private OrbitalWeaponAdministrationDispatcher() {}

    /** Moves one endpoint to a dense owner-selected failover rank. */
    public static boolean setEndpointPriority(
                                               ServerPlayer actor,
                                               UUID weaponId,
                                               OrbitalEndpointLocation location,
                                               int priority) {
        MinecraftServer server = actor.getServer();
        if (server == null || !server.isSameThread()) {
            return false;
        }
        return OrbitalWeaponSavedData.get(server).setEndpointPriority(
                server,
                actor.getUUID(),
                weaponId,
                location,
                priority);
    }

    /** Selects one online uplink beacon as the primary projection anchor. */
    public static boolean selectPrimaryAnchor(
                                               ServerPlayer actor,
                                               UUID weaponId,
                                               OrbitalEndpointLocation location) {
        MinecraftServer server = actor.getServer();
        if (server == null || !server.isSameThread()) {
            return false;
        }
        return OrbitalWeaponSavedData.get(server).selectPrimaryAnchor(
                server,
                actor.getUUID(),
                weaponId,
                location);
    }

    /** Adds or replaces one delegated role after the owner permission is rechecked. */
    public static boolean authorize(
                                    ServerPlayer actor,
                                    UUID weaponId,
                                    UUID playerId,
                                    OrbitalAccessRole role) {
        MinecraftServer server = actor.getServer();
        if (server == null || !server.isSameThread()) {
            return false;
        }
        OrbitalWeaponSavedData.get(server).authorize(
                server,
                weaponId,
                actor.getUUID(),
                playerId,
                role);
        return true;
    }

    /** Removes one delegated role after the owner permission is rechecked. */
    public static boolean revoke(ServerPlayer actor, UUID weaponId, UUID playerId) {
        MinecraftServer server = actor.getServer();
        if (server == null || !server.isSameThread()) {
            return false;
        }
        OrbitalWeaponSavedData.get(server).revoke(
                server,
                weaponId,
                actor.getUUID(),
                playerId);
        return true;
    }
}
