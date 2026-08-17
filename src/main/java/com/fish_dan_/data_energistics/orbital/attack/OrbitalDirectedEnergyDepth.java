package com.fish_dan_.data_energistics.orbital.attack;

import net.minecraft.server.level.ServerLevel;

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

    DEPTH_32(0, 32, false),
    DEPTH_128(1, 128, false),
    DEPTH_512(2, 512, false),
    THROUGH(3, 0, true);

    private final int wireCode;
    private final int depth;
    @Getter
    private final boolean through;

    OrbitalDirectedEnergyDepth(int wireCode, int depth, boolean through) {
        this.wireCode = wireCode;
        this.depth = depth;
        this.through = through;
    }

    /**
     * Returns the inclusive bottom Y coordinate for a target in one level.
     */
    public int bottomY(ServerLevel level, int targetY) {
        return this.through ? level.getMinBuildHeight() : Math.max(level.getMinBuildHeight(), targetY - this.depth);
    }

    public int configuredDepth() {
        return this.depth;
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
