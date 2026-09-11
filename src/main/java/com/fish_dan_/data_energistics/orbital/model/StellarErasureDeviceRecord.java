package com.fish_dan_.data_energistics.orbital.model;

import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointKind;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointLocation;
import com.fish_dan_.data_energistics.orbital.endpoint.OrbitalEndpointRecord;
import com.fish_dan_.data_energistics.orbital.reserve.OrbitalEnergyReserve;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
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
public record StellarErasureDeviceRecord(
                                  UUID weaponId,
                                  UUID ownerId,
                                  Map<UUID, OrbitalAccessRole> delegatedRoles,
                                  Map<OrbitalEndpointLocation, OrbitalEndpointRecord> endpoints,
                                  OrbitalEnergyReserve reserve,
                                  StellarErasureDeviceLifecycle lifecycle,
                                  @Nullable OrbitalEndpointLocation primaryAnchor,
                                  String customName) {

    public static final int MAX_NAME_LENGTH = 48;

    /** Normalizes player/NBT text. Empty text restores the default identifier label. */
    public static String normalizeName(String name) {
        String normalized = name.strip();
        if (name.length() > MAX_NAME_LENGTH || name.codePoints().anyMatch(
                point -> Character.isISOControl(point) || point == 0xA7 || Character.getType(point) == Character.FORMAT)) {
            throw new IllegalArgumentException("Weapon name must be at most 48 characters without control or formatting codes");
        }
        return normalized;
    }

    /** Returns a renamed record; stable identity and every operational field are preserved. */
    public StellarErasureDeviceRecord withName(String name) {
        return new StellarErasureDeviceRecord(weaponId, ownerId, delegatedRoles, endpoints, reserve, lifecycle, primaryAnchor, name);
    }

    public StellarErasureDeviceRecord {
        customName = normalizeName(customName);
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
    public static StellarErasureDeviceRecord create(UUID weaponId, UUID ownerId) {
        return new StellarErasureDeviceRecord(
                weaponId,
                ownerId,
                Map.of(),
                Map.of(),
                OrbitalEnergyReserve.empty(),
                StellarErasureDeviceLifecycle.dormant(),
                null,
                "");
    }

    /**
     * Evaluates a server-authoritative action against the current ownership snapshot.
     */
    public boolean canPerform(UUID playerId, StellarErasureDeviceAction action) {
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
    public StellarErasureDeviceRecord withRole(UUID playerId, OrbitalAccessRole role) {
        if (this.ownerId.equals(playerId)) {
            throw new IllegalArgumentException("The owner must not also have a delegated role");
        }
        if (role == this.delegatedRoles.get(playerId)) {
            return this;
        }

        Map<UUID, OrbitalAccessRole> updatedRoles = new Object2ObjectOpenHashMap<>(this.delegatedRoles);
        updatedRoles.put(playerId, role);
        return new StellarErasureDeviceRecord(
                this.weaponId,
                this.ownerId,
                updatedRoles,
                this.endpoints,
                this.reserve,
                this.lifecycle,
                this.primaryAnchor,
                this.customName);
    }

    /**
     * Returns a new record without the player's delegated role.
     */
    public StellarErasureDeviceRecord withoutRole(UUID playerId) {
        if (this.ownerId.equals(playerId)) {
            throw new IllegalArgumentException("Ownership cannot be revoked as a delegated role");
        }
        if (!this.delegatedRoles.containsKey(playerId)) {
            return this;
        }

        Map<UUID, OrbitalAccessRole> updatedRoles = new Object2ObjectOpenHashMap<>(this.delegatedRoles);
        updatedRoles.remove(playerId);
        return new StellarErasureDeviceRecord(
                this.weaponId,
                this.ownerId,
                updatedRoles,
                this.endpoints,
                this.reserve,
                this.lifecycle,
                this.primaryAnchor,
                this.customName);
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
    public StellarErasureDeviceRecord withEndpoint(OrbitalEndpointRecord endpoint) {
        OrbitalEndpointRecord existing = this.endpoints.get(endpoint.location());
        if (endpoint.equals(existing)) {
            return this;
        }

        Map<OrbitalEndpointLocation, OrbitalEndpointRecord> updatedEndpoints = new Object2ObjectOpenHashMap<>(this.endpoints);
        updatedEndpoints.put(endpoint.location(), endpoint);
        return new StellarErasureDeviceRecord(
                this.weaponId,
                this.ownerId,
                this.delegatedRoles,
                updatedEndpoints,
                this.reserve,
                this.lifecycle,
                this.primaryAnchor,
                this.customName);
    }

    /**
     * Returns a new record without the endpoint at the supplied location.
     */
    public StellarErasureDeviceRecord withoutEndpoint(OrbitalEndpointLocation location) {
        if (!this.endpoints.containsKey(location)) {
            return this;
        }

        Map<OrbitalEndpointLocation, OrbitalEndpointRecord> updatedEndpoints = new Object2ObjectOpenHashMap<>(this.endpoints);
        updatedEndpoints.remove(location);
        OrbitalEndpointLocation updatedAnchor = location.equals(this.primaryAnchor) ? null : this.primaryAnchor;
        return new StellarErasureDeviceRecord(
                this.weaponId,
                this.ownerId,
                this.delegatedRoles,
                updatedEndpoints,
                this.reserve,
                this.lifecycle,
                updatedAnchor,
                this.customName);
    }

    /**
     * Returns a new record containing the supplied persistent energy reserve.
     */
    public StellarErasureDeviceRecord withReserve(OrbitalEnergyReserve reserve) {
        if (this.reserve.equals(reserve)) {
            return this;
        }
        return new StellarErasureDeviceRecord(
                this.weaponId,
                this.ownerId,
                this.delegatedRoles,
                this.endpoints,
                reserve,
                this.lifecycle,
                this.primaryAnchor,
                this.customName);
    }

    /** Returns a new record with the supplied deployment state. */
    public StellarErasureDeviceRecord withLifecycle(StellarErasureDeviceLifecycle lifecycle) {
        if (this.lifecycle.equals(lifecycle)) {
            return this;
        }
        return new StellarErasureDeviceRecord(
                this.weaponId,
                this.ownerId,
                this.delegatedRoles,
                this.endpoints,
                this.reserve,
                lifecycle,
                this.primaryAnchor,
                this.customName);
    }

    /** Returns a new record with the owner-selected uplink beacon as the projection anchor. */
    public StellarErasureDeviceRecord withPrimaryAnchor(@Nullable OrbitalEndpointLocation primaryAnchor) {
        if (Objects.equals(this.primaryAnchor, primaryAnchor)) {
            return this;
        }
        return new StellarErasureDeviceRecord(
                this.weaponId,
                this.ownerId,
                this.delegatedRoles,
                this.endpoints,
                this.reserve,
                this.lifecycle,
                primaryAnchor,
                this.customName);
    }

    /**
     * Returns whether the weapon is deployed and may accept a new attack escrow.
     *
     * <p>
     * The primary anchor controls the public projection location; a control console remains a valid operational
     * endpoint even before an uplink beacon has been selected.
     * </p>
     */
    public boolean allowsNewAttacks() {
        return this.lifecycle.allowsNewAttacks();
    }
}
