package com.fish_dan_.data_energistics.orbital.attack;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

import lombok.Getter;

/**
 * Server-authoritative depth profiles for the spiral directed-energy scan.
 *
 * <p>
 * The profile is captured in the attack record. A configuration reload therefore cannot change an already
 * reserved scan's geometry.
 * </p>
 */
public enum OrbitalDirectedEnergyDepth {

    DEPTH_32(0, false),
    DEPTH_128(1, false),
    DEPTH_512(2, false),
    THROUGH(3, true);

    private final int wireCode;
    @Getter
    private final boolean through;

    OrbitalDirectedEnergyDepth(int wireCode, boolean through) {
        this.wireCode = wireCode;
        this.through = through;
    }

    /** Resolves this stable wire profile to the current server value before geometry is frozen. */
    public int configuredDepth(DataEnergisticsConfiguration.OrbitalWeaponSchema settings) {
        return switch (this) {
            case DEPTH_32 -> settings.directedEnergyShallowDepth;
            case DEPTH_128 -> settings.directedEnergyMediumDepth;
            case DEPTH_512 -> settings.directedEnergyDeepDepth;
            case THROUGH -> 0;
        };
    }

    /** Returns the stable network code without exposing enum declaration order. */
    public int wireCode() {
        return this.wireCode;
    }

    /** Decodes one bounded network code and rejects unknown future values. */
    public static OrbitalDirectedEnergyDepth fromWireCode(int wireCode) {
        return switch (wireCode) {
            case 0 -> DEPTH_32;
            case 1 -> DEPTH_128;
            case 2 -> DEPTH_512;
            case 3 -> THROUGH;
            default -> throw new IllegalArgumentException("Unknown directed-energy depth code: " + wireCode);
        };
    }
}
