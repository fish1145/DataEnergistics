package com.fish_dan_.data_energistics.orbital.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable authoritative state for one orbital weapon.
 *
 * <p>
 * The record begins with ownership and delegated access. Later feature slices extend the same record with
 * lifecycle, reserve, endpoint and attack state without changing the stable weapon identity.
 * </p>
 */
public record OrbitalWeaponRecord(
                                  UUID weaponId,
                                  UUID ownerId,
                                  Map<UUID, OrbitalAccessRole> delegatedRoles) {

    public OrbitalWeaponRecord {
        delegatedRoles = Map.copyOf(delegatedRoles);
        if (delegatedRoles.containsKey(ownerId)) {
            throw new IllegalArgumentException("The owner must not also have a delegated role");
        }
    }

    /**
     * Creates an unshared weapon record with a stable weapon identity.
     */
    public static OrbitalWeaponRecord create(UUID weaponId, UUID ownerId) {
        return new OrbitalWeaponRecord(weaponId, ownerId, Map.of());
    }

    /**
     * Evaluates a server-authoritative action against the current ownership snapshot.
     */
    public boolean canPerform(UUID playerId, OrbitalWeaponAction action) {
        return OrbitalAccessPolicy.canPerform(this.ownerId, this.delegatedRoles, playerId, action);
    }

    /**
     * Freezes the players protected from damage by an attack confirmed from this record.
     */
    public Set<UUID> damageExemptionSnapshot() {
        return OrbitalAccessPolicy.damageExemptionSnapshot(this.ownerId, this.delegatedRoles);
    }

    /**
     * Returns a new record with the delegated role added or replaced.
     */
    public OrbitalWeaponRecord withRole(UUID playerId, OrbitalAccessRole role) {
        if (this.ownerId.equals(playerId)) {
            throw new IllegalArgumentException("The owner must not also have a delegated role");
        }
        if (role == this.delegatedRoles.get(playerId)) {
            return this;
        }

        HashMap<UUID, OrbitalAccessRole> updatedRoles = new HashMap<>(this.delegatedRoles);
        updatedRoles.put(playerId, role);
        return new OrbitalWeaponRecord(this.weaponId, this.ownerId, updatedRoles);
    }

    /**
     * Returns a new record without the player's delegated role.
     */
    public OrbitalWeaponRecord withoutRole(UUID playerId) {
        if (this.ownerId.equals(playerId)) {
            throw new IllegalArgumentException("Ownership cannot be revoked as a delegated role");
        }
        if (!this.delegatedRoles.containsKey(playerId)) {
            return this;
        }

        HashMap<UUID, OrbitalAccessRole> updatedRoles = new HashMap<>(this.delegatedRoles);
        updatedRoles.remove(playerId);
        return new OrbitalWeaponRecord(this.weaponId, this.ownerId, updatedRoles);
    }
}
