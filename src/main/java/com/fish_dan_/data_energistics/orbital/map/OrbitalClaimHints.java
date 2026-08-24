package com.fish_dan_.data_energistics.orbital.map;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.server.level.ServerLevel;

import java.util.Objects;

/** Optional, read-only claim lookup used only to annotate tactical-map cells. */
public final class OrbitalClaimHints {

    private static final Lookup NONE = (level, chunkX, chunkZ) -> false;

    private static volatile Lookup lookup = NONE;
    private static volatile boolean failed;

    private OrbitalClaimHints() {}

    /** Installs one optional provider during common setup; replacing it does not alter attack authorization. */
    public static void install(Lookup provider) {
        lookup = Objects.requireNonNull(provider);
        failed = false;
    }

    /**
     * Returns a display-only claim hint on the server thread and permanently isolates a failing optional provider.
     */
    public static boolean isClaimed(ServerLevel level, int chunkX, int chunkZ) {
        try {
            return lookup.isClaimed(level, chunkX, chunkZ);
        } catch (RuntimeException | LinkageError exception) {
            if (!failed) {
                failed = true;
                lookup = NONE;
                Data_Energistics.LOGGER.error(
                        "Disabling orbital tactical-map claim hints after the optional provider failed",
                        exception);
            }
            return false;
        }
    }

    /**
     * Reads claim presence without loading chunks or applying claim permissions.
     *
     * <p>
     * Implementations are installed once during common setup and invoked only from the server-thread tactical-map
     * request path. They must accept every loaded dimension, return {@code false} when no claim exists, never mutate
     * claim state and never load or generate terrain.
     * </p>
     */
    @FunctionalInterface
    public interface Lookup {

        /** Returns whether the supplied chunk has a displayable claim; this call must be read-only and non-loading. */
        boolean isClaimed(ServerLevel level, int chunkX, int chunkZ);
    }
}
