package com.fish_dan_.data_energistics.orbital.model;

import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointKind;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointRecord;
import com.fish_dan_.data_energistics.orbital.reserve.OrbitalEnergyReserve;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable authoritative state for one orbital weapon.
 *
 * <p>
 * The record begins with ownership, delegated access and physical endpoints. Later feature slices extend the same
 * record with lifecycle, reserve and attack state without changing the stable weapon identity.
 * </p>
 */
public record OrbitalWeaponRecord(
                                  UUID weaponId,
                                  UUID ownerId,
                                  Map<UUID, OrbitalAccessRole> delegatedRoles,
                                  Map<OrbitalEndpointLocation, OrbitalEndpointRecord> endpoints,
                                  OrbitalEnergyReserve reserve,
                                  OrbitalWeaponLifecycle lifecycle,
                                  @Nullable OrbitalEndpointLocation primaryAnchor) {

    /** Compatibility constructor for records written before lifecycle state was persisted. */
    public OrbitalWeaponRecord(
                               UUID weaponId,
                               UUID ownerId,
                               Map<UUID, OrbitalAccessRole> delegatedRoles,
                               Map<OrbitalEndpointLocation, OrbitalEndpointRecord> endpoints,
                               OrbitalEnergyReserve reserve) {
        this(weaponId, ownerId, delegatedRoles, endpoints, reserve, OrbitalWeaponLifecycle.dormant(), null);
    }

    /** Compatibility constructor for records created before the primary anchor was persisted. */
    public OrbitalWeaponRecord(
                               UUID weaponId,
                               UUID ownerId,
                               Map<UUID, OrbitalAccessRole> delegatedRoles,
                               Map<OrbitalEndpointLocation, OrbitalEndpointRecord> endpoints,
                               OrbitalEnergyReserve reserve,
                               OrbitalWeaponLifecycle lifecycle) {
        this(weaponId, ownerId, delegatedRoles, endpoints, reserve, lifecycle, null);
    }

    public OrbitalWeaponRecord {
        delegatedRoles = Map.copyOf(delegatedRoles);
        endpoints = Map.copyOf(endpoints);
        if (delegatedRoles.containsKey(ownerId)) {
            throw new IllegalArgumentException("The owner must not also have a delegated role");
        }
        for (Map.Entry<OrbitalEndpointLocation, OrbitalEndpointRecord> entry : endpoints.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().location())) {
                throw new IllegalArgumentException("Endpoint map key must match its record location");
            }
        }
        if (primaryAnchor != null) {
            OrbitalEndpointRecord anchor = endpoints.get(primaryAnchor);
            if (anchor == null || anchor.kind() != OrbitalEndpointKind.UPLINK_BEACON) {
                throw new IllegalArgumentException("Primary anchor must reference a bound uplink beacon");
            }
        }
    }

    /**
     * Creates an unshared weapon record with a stable weapon identity.
     */
    public static OrbitalWeaponRecord create(UUID weaponId, UUID ownerId) {
        return new OrbitalWeaponRecord(weaponId, ownerId, Map.of(), Map.of(), OrbitalEnergyReserve.empty());
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
        return new OrbitalWeaponRecord(
                this.weaponId,
                this.ownerId,
                updatedRoles,
                this.endpoints,
                this.reserve,
                this.lifecycle,
                this.primaryAnchor);
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
        return new OrbitalWeaponRecord(
                this.weaponId,
                this.ownerId,
                updatedRoles,
                this.endpoints,
                this.reserve,
                this.lifecycle,
                this.primaryAnchor);
    }

    /**
     * Returns the next append-only endpoint priority without rewriting existing ordering.
     */
    public int nextEndpointPriority() {
        int highestPriority = -1;
        for (OrbitalEndpointRecord endpoint : this.endpoints.values()) {
            highestPriority = Math.max(highestPriority, endpoint.priority());
        }
        if (highestPriority == Integer.MAX_VALUE) {
            throw new IllegalStateException("Endpoint priority space is exhausted for weapon " + this.weaponId);
        }
        return highestPriority + 1;
    }

    /**
     * Returns a new record with an endpoint added at its dimension-qualified location.
     */
    public OrbitalWeaponRecord withEndpoint(OrbitalEndpointRecord endpoint) {
        OrbitalEndpointRecord existing = this.endpoints.get(endpoint.location());
        if (endpoint.equals(existing)) {
            return this;
        }

        HashMap<OrbitalEndpointLocation, OrbitalEndpointRecord> updatedEndpoints = new HashMap<>(this.endpoints);
        updatedEndpoints.put(endpoint.location(), endpoint);
        return new OrbitalWeaponRecord(
                this.weaponId,
                this.ownerId,
                this.delegatedRoles,
                updatedEndpoints,
                this.reserve,
                this.lifecycle,
                this.primaryAnchor);
    }

    /**
     * Returns a new record without the endpoint at the supplied location.
     */
    public OrbitalWeaponRecord withoutEndpoint(OrbitalEndpointLocation location) {
        if (!this.endpoints.containsKey(location)) {
            return this;
        }

        HashMap<OrbitalEndpointLocation, OrbitalEndpointRecord> updatedEndpoints = new HashMap<>(this.endpoints);
        updatedEndpoints.remove(location);
        OrbitalEndpointLocation updatedAnchor = location.equals(this.primaryAnchor) ? null : this.primaryAnchor;
        return new OrbitalWeaponRecord(
                this.weaponId,
                this.ownerId,
                this.delegatedRoles,
                updatedEndpoints,
                this.reserve,
                this.lifecycle,
                updatedAnchor);
    }

    /**
     * Returns a new record containing the supplied persistent energy reserve.
     */
    public OrbitalWeaponRecord withReserve(OrbitalEnergyReserve reserve) {
        if (this.reserve.equals(reserve)) {
            return this;
        }
        return new OrbitalWeaponRecord(
                this.weaponId,
                this.ownerId,
                this.delegatedRoles,
                this.endpoints,
                reserve,
                this.lifecycle,
                this.primaryAnchor);
    }

    /** Returns a new record with the supplied deployment state. */
    public OrbitalWeaponRecord withLifecycle(OrbitalWeaponLifecycle lifecycle) {
        if (this.lifecycle.equals(lifecycle)) {
            return this;
        }
        return new OrbitalWeaponRecord(
                this.weaponId,
                this.ownerId,
                this.delegatedRoles,
                this.endpoints,
                this.reserve,
                lifecycle,
                this.primaryAnchor);
    }

    /** Returns a new record with the owner-selected uplink beacon as the projection anchor. */
    public OrbitalWeaponRecord withPrimaryAnchor(@Nullable OrbitalEndpointLocation primaryAnchor) {
        if (this.primaryAnchor == primaryAnchor
                || (this.primaryAnchor != null && this.primaryAnchor.equals(primaryAnchor))) {
            return this;
        }
        return new OrbitalWeaponRecord(
                this.weaponId,
                this.ownerId,
                this.delegatedRoles,
                this.endpoints,
                this.reserve,
                this.lifecycle,
                primaryAnchor);
    }

    /** Returns whether the weapon is deployed and may accept a new attack escrow. */
    public boolean allowsNewAttacks() {
        return this.lifecycle.allowsNewAttacks();
    }
}
