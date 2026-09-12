package com.fish_dan_.data_energistics.orbital.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Delegated access roles. Ownership remains a separate weapon-record field and is never represented by this enum.
 */
public enum OrbitalAccessRole {

    OPERATOR(EnumSet.of(
            StellarErasureDeviceAction.VIEW_STATUS,
            StellarErasureDeviceAction.AIM,
            StellarErasureDeviceAction.FIRE,
            StellarErasureDeviceAction.CANCEL_WARNING_ATTACK,
            StellarErasureDeviceAction.EMERGENCY_ABORT)),
    OBSERVER(EnumSet.of(StellarErasureDeviceAction.VIEW_STATUS));

    private final Set<StellarErasureDeviceAction> allowedActions;

    OrbitalAccessRole(Set<StellarErasureDeviceAction> allowedActions) {
        this.allowedActions = Set.copyOf(allowedActions);
    }

    /**
     * Returns whether this delegated role permits the requested server action.
     */
    public boolean allows(StellarErasureDeviceAction action) {
        return this.allowedActions.contains(action);
    }
}
