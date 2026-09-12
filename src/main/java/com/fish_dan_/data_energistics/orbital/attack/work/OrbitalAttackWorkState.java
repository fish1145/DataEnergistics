package com.fish_dan_.data_energistics.orbital.attack.work;

/**
 * Persisted terrain-work boundary for a committed orbital attack.
 */
public enum OrbitalAttackWorkState {
    INACTIVE,
    WAITING_FOR_CHUNK,
    WAITING_FOR_BUDGET,
    WORKING
}
