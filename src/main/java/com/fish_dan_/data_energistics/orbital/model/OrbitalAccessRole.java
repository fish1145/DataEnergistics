package com.fish_dan_.data_energistics.orbital.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Delegated access roles. Ownership remains a separate weapon-record field and is never represented by this enum.
 */
public enum OrbitalAccessRole {

    OPERATOR(EnumSet.of(
            OrbitalWeaponAction.VIEW_STATUS,
            OrbitalWeaponAction.AIM,
            OrbitalWeaponAction.FIRE,
            OrbitalWeaponAction.CANCEL_WARNING_ATTACK,
            OrbitalWeaponAction.EMERGENCY_ABORT)),
    OBSERVER(EnumSet.of(OrbitalWeaponAction.VIEW_STATUS));

    private final Set<OrbitalWeaponAction> allowedActions;

    OrbitalAccessRole(Set<OrbitalWeaponAction> allowedActions) {
        this.allowedActions = Set.copyOf(allowedActions);
    }

    /**
     * Returns whether this delegated role permits the requested server action.
     */
    public boolean allows(OrbitalWeaponAction action) {
        return this.allowedActions.contains(action);
    }
}
