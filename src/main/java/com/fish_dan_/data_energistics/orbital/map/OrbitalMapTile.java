package com.fish_dan_.data_energistics.orbital.map;

/** One chunk-sized tactical-map cell; unknown cells deliberately carry no generated terrain data. */
public record OrbitalMapTile(
                              int chunkX,
                              int chunkZ,
                              boolean known,
                              int surfaceY,
                              int biomeColor,
                              int markerFlags) {

    public static final int UNKNOWN_SURFACE = Integer.MIN_VALUE;
    public static final int UNKNOWN_BIOME_COLOR = 0;
    public static final int MARKER_UPLINK_BEACON = 1;
    public static final int MARKER_PRIMARY_ANCHOR = 1 << 1;
    public static final int MARKER_ACTIVE_PUBLIC_ATTACK = 1 << 2;
    public static final int KNOWN_MARKERS = MARKER_UPLINK_BEACON
            | MARKER_PRIMARY_ANCHOR
            | MARKER_ACTIVE_PUBLIC_ATTACK;

    /** Compatibility constructor for the original four-field map tile. */
    public OrbitalMapTile(int chunkX, int chunkZ, boolean known, int surfaceY) {
        this(chunkX, chunkZ, known, surfaceY, UNKNOWN_BIOME_COLOR, 0);
    }

    public OrbitalMapTile {
        if (known && surfaceY == UNKNOWN_SURFACE) {
            throw new IllegalArgumentException("A known orbital map tile must carry a surface height");
        }
        if (!known && surfaceY != UNKNOWN_SURFACE) {
            throw new IllegalArgumentException("An unknown orbital map tile cannot carry terrain data");
        }
        if (!known && biomeColor != UNKNOWN_BIOME_COLOR) {
            throw new IllegalArgumentException("An unknown orbital map tile cannot carry a biome color");
        }
        if (biomeColor < 0 || biomeColor > 0xFFFFFF) {
            throw new IllegalArgumentException("Biome color must be an RGB value");
        }
        if ((markerFlags & ~KNOWN_MARKERS) != 0) {
            throw new IllegalArgumentException("Unknown tactical-map marker bits are not allowed");
        }
    }

    /** Creates a cell for a chunk that is not currently loaded. */
    public static OrbitalMapTile unknown(int chunkX, int chunkZ) {
        return unknown(chunkX, chunkZ, 0);
    }

    /** Creates an unknown terrain cell while retaining public markers from authoritative server state. */
    public static OrbitalMapTile unknown(int chunkX, int chunkZ, int markerFlags) {
        return new OrbitalMapTile(chunkX, chunkZ, false, UNKNOWN_SURFACE, UNKNOWN_BIOME_COLOR, markerFlags);
    }
}
