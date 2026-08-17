package com.fish_dan_.data_energistics.orbital.attack;

import net.minecraft.server.level.ServerLevel;

/**
 * Server-authoritative depth profiles for the spiral directed-energy scan.
 *
 * <p>
 * The profile is captured in the attack record. A configuration reload therefore cannot change an already
 * reserved scan's geometry.
 * </p>
 */
public enum OrbitalDirectedEnergyDepth {

    DEPTH_32(32, false),
    DEPTH_128(128, false),
    DEPTH_512(512, false),
    THROUGH(0, true);

    private final int depth;
    private final boolean through;

    OrbitalDirectedEnergyDepth(int depth, boolean through) {
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

    public boolean isThrough() {
        return this.through;
    }
}
