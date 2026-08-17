package com.fish_dan_.data_energistics.orbital.map;

/** One chunk-sized tactical-map cell; unknown cells deliberately carry no generated terrain data. */
public record OrbitalMapTile(int chunkX, int chunkZ, boolean known, int surfaceY) {

    public static final int UNKNOWN_SURFACE = Integer.MIN_VALUE;

    public OrbitalMapTile {
        if (known && surfaceY == UNKNOWN_SURFACE) {
            throw new IllegalArgumentException("A known orbital map tile must carry a surface height");
        }
        if (!known && surfaceY != UNKNOWN_SURFACE) {
            throw new IllegalArgumentException("An unknown orbital map tile cannot carry terrain data");
        }
    }

    /** Creates a cell for a chunk that is not currently loaded. */
    public static OrbitalMapTile unknown(int chunkX, int chunkZ) {
        return new OrbitalMapTile(chunkX, chunkZ, false, UNKNOWN_SURFACE);
    }
}
