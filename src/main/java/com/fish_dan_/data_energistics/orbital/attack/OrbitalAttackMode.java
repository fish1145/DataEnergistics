package com.fish_dan_.data_energistics.orbital.attack;

/**
 * Orbital weapon firing modes. The kinetic mode is the first executable mode; the other modes are added by later
 * slices without changing the saved attack identity model.
 */
public enum OrbitalAttackMode {
    KINETIC,
    DIRECTED_ENERGY,
    DIGITAL_ANNIHILATION
}
