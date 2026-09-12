package com.fish_dan_.data_energistics.orbital.attack.entity.strike;

/** Actual per-subject result; only terminal results are persisted after an execution returns. */
public enum OrbitalErasureOutcome {
    IN_PROGRESS,
    EXEMPT,
    INACTIVE,
    ALREADY_HANDLED,
    ENTITY_REMOVED,
    PLAYER_DEATH_COMPLETED,
    GUARDIAN_ENCOUNTER_COMPLETED,
    FAILED_BEFORE_COMMIT,
    PARTIAL_FAILURE
}
