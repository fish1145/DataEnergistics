package com.fish_dan_.data_energistics.orbital.attack.beam;

import net.minecraft.core.BlockPos;

/** Server-completed work span for one tick; its end is the snapshot work cursor, exclusive. */
public record OrbitalBeamSweep(int topY, int bottomY, OrbitalBeamPath path, long fromCursor) {

    public OrbitalBeamSweep {
        if (bottomY > topY || (long) topY - bottomY >= 4096 || fromCursor < 0) {
            throw new IllegalArgumentException("Invalid directed-energy visual sweep");
        }
    }

    public OrbitalBeamScan scan(BlockPos target, int radius) {
        return new OrbitalBeamScan(target, this.topY, this.bottomY, radius, this.path);
    }
}
