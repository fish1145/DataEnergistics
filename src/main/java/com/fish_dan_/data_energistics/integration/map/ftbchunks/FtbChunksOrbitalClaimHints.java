package com.fish_dan_.data_energistics.integration.map.ftbchunks;

import net.minecraft.server.level.ServerLevel;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;

/** Exact-version FTB Chunks claim lookup for display-only orbital tactical-map markers. */
public final class FtbChunksOrbitalClaimHints {

    private FtbChunksOrbitalClaimHints() {}

    /** Reads one claim entry without consulting protection rules or mutating FTB team data. */
    public static boolean isClaimed(ServerLevel level, int chunkX, int chunkZ) {
        FTBChunksAPI.API api = FTBChunksAPI.api();
        return api.isManagerLoaded() &&
                api.getManager().getChunk(new ChunkDimPos(level.dimension(), chunkX, chunkZ)) != null;
    }
}
