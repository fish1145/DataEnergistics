package com.fish_dan_.data_energistics.orbital.map;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/** Bounded, revisioned server result for one tactical-map viewport request. */
public record OrbitalTacticalMapSnapshot(
                                         UUID weaponId,
                                         UUID sessionToken,
                                         long requestNonce,
                                         long revision,
                                         ResourceLocation dimensionId,
                                         int centerChunkX,
                                         int centerChunkZ,
                                         int radius,
                                         List<OrbitalMapTile> tiles) {

    public static final int MAX_TILES = 64;

    public OrbitalTacticalMapSnapshot {
        tiles = List.copyOf(tiles);
        if (requestNonce <= 0L || revision < 0L || radius < 0 || radius > 3 || tiles.size() > MAX_TILES) {
            throw new IllegalArgumentException("Orbital tactical-map snapshot exceeds its bounded range");
        }
        int expected = (radius * 2 + 1) * (radius * 2 + 1);
        if (tiles.size() != expected) {
            throw new IllegalArgumentException("Orbital tactical-map snapshot does not contain a complete viewport");
        }
    }
}
