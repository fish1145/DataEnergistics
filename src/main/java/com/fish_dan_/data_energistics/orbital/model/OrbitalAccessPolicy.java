package com.fish_dan_.data_energistics.orbital.model;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Evaluates owner and delegated-role access without trusting client-side UI state.
 */
public final class OrbitalAccessPolicy {

    private OrbitalAccessPolicy() {}

    /**
     * Resolves one action against the immutable owner identity and current delegated-role snapshot.
     */
    public static boolean canPerform(
                                     UUID ownerId,
                                     Map<UUID, OrbitalAccessRole> delegatedRoles,
                                     UUID playerId,
                                     OrbitalWeaponAction action) {
        if (ownerId.equals(playerId)) {
            return true;
        }
        OrbitalAccessRole role = delegatedRoles.get(playerId);
        return role != null && role.allows(action);
    }

    /**
     * Captures every currently authorized UUID for attack damage exemption. Later role changes do not mutate the
     * returned snapshot.
     */
    public static Set<UUID> damageExemptionSnapshot(
                                                    UUID ownerId,
                                                    Map<UUID, OrbitalAccessRole> delegatedRoles) {
        HashSet<UUID> exemptions = new HashSet<>(delegatedRoles.keySet());
        exemptions.add(ownerId);
        return Set.copyOf(exemptions);
    }
}
