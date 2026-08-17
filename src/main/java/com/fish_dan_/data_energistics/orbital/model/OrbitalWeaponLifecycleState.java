package com.fish_dan_.data_energistics.orbital.model;

/**
 * Server-authoritative lifecycle of an orbital weapon projection.
 *
 * <p>
 * A newly provisioned record is dormant. Only a deployed record may reserve a new attack; a reserve-grace record
 * keeps already escrowed attacks alive while refusing new confirmations.
 * </p>
 */
public enum OrbitalWeaponLifecycleState {
    DORMANT,
    DEPLOYED,
    RESERVE_GRACE
}
