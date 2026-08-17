package com.fish_dan_.data_energistics.orbital.attack;

/**
 * Orbital weapon firing modes. The kinetic mode is the first executable mode; the other modes are added by later
 * slices without changing the saved attack identity model.
 */
public enum OrbitalAttackMode {
    KINETIC(0),
    DIRECTED_ENERGY(1),
    DIGITAL_ANNIHILATION(2);

    private final int wireCode;

    OrbitalAttackMode(int wireCode) {
        this.wireCode = wireCode;
    }

    /** Returns the stable network code without exposing enum declaration order. */
    public int wireCode() {
        return this.wireCode;
    }

    /** Decodes one bounded network code and rejects unknown future values. */
    public static OrbitalAttackMode fromWireCode(int wireCode) {
        return switch (wireCode) {
            case 0 -> KINETIC;
            case 1 -> DIRECTED_ENERGY;
            case 2 -> DIGITAL_ANNIHILATION;
            default -> throw new IllegalArgumentException("Unknown orbital attack mode code: " + wireCode);
        };
    }
}
