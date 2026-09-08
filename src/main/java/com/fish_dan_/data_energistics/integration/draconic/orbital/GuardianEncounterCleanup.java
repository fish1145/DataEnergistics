package com.fish_dan_.data_energistics.integration.draconic.orbital;

/**
 * Narrow bridge to Draconic's resource-only encounter cleanup. It exists because removeEntity alone leaves the
 * boss bar and chunk ticket behind. Call only on the encounter's server thread after a partially failed terminal
 * callback; it must not award loot or repeat processDragonDeath. Successful normal termination needs no bridge call.
 */
public interface GuardianEncounterCleanup {

    /** Releases the encounter's ticket, viewers and world-entity registration; may propagate a cleanup failure. */
    void dataEnergistics$releaseErasedEncounter();
}
