package com.fish_dan_.data_energistics.client;

import com.fish_dan_.data_energistics.network.meteorite.DataMeteoriteCompassRequestPayload;
import com.fish_dan_.data_energistics.network.meteorite.DataMeteoriteCompassResponsePayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

import appeng.core.definitions.AEBlocks;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class DataMeteoriteCompassClientCache {

    private static final long REFRESH_AFTER_MS = 30_000L;
    private static final long EMPTY_REFRESH_AFTER_MS = 1_000L;
    private static final long EXPIRE_AFTER_MS = 60_000L;
    private static final Map<Long, CachedResult> REQUESTS = new HashMap<>();

    private DataMeteoriteCompassClientCache() {}

    public static void cacheSyncedCompassResult(DataMeteoriteCompassResponsePayload payload) {
        REQUESTS.put(payload.requestedPos().toLong(), new CachedResult(payload.closestMeteorite().orElse(null), System.currentTimeMillis()));
    }

    @Nullable
    public static BlockPos getClosestMeteorite(ChunkPos chunkPos, boolean prefetch) {
        long now = System.currentTimeMillis();
        expireOldResults(now);
        ClientLevel level = Minecraft.getInstance().level;

        CachedResult cached = REQUESTS.get(chunkPos.toLong());
        BlockPos result = cached == null ? null : cached.closestMeteoritePos();
        if (!isTargetStillPresent(level, result)) {
            REQUESTS.remove(chunkPos.toLong());
            cached = null;
            result = null;
        }

        boolean request = cached == null || shouldRefresh(cached, now);
        if (result == null) {
            result = findClosestKnownResult(level, chunkPos);
        }

        if (request) {
            REQUESTS.put(chunkPos.toLong(), new CachedResult(result, now));
            PacketDistributor.sendToServer(new DataMeteoriteCompassRequestPayload(chunkPos));
        }

        if (prefetch) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        getClosestMeteorite(new ChunkPos(chunkPos.x + dx, chunkPos.z + dz), false);
                    }
                }
            }
        }

        return result;
    }

    private static boolean shouldRefresh(CachedResult cached, long now) {
        long refreshAfter = cached.closestMeteoritePos() == null ? EMPTY_REFRESH_AFTER_MS : REFRESH_AFTER_MS;
        return now - cached.received() > refreshAfter;
    }

    private static void expireOldResults(long now) {
        Iterator<CachedResult> iterator = REQUESTS.values().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().received() > EXPIRE_AFTER_MS) {
                iterator.remove();
            }
        }
    }

    @Nullable
    private static BlockPos findClosestKnownResult(@Nullable ClientLevel level, ChunkPos chunkPos) {
        long closestDistance = Long.MAX_VALUE;
        BlockPos result = null;
        for (Map.Entry<Long, CachedResult> entry : REQUESTS.entrySet()) {
            BlockPos closestPos = entry.getValue().closestMeteoritePos();
            if (closestPos != null && isTargetStillPresent(level, closestPos)) {
                long distance = chunkPos.distanceSquared(entry.getKey());
                if (distance < closestDistance) {
                    closestDistance = distance;
                    result = closestPos;
                }
            }
        }
        return result;
    }

    private static boolean isTargetStillPresent(@Nullable ClientLevel level, @Nullable BlockPos pos) {
        return pos == null || level == null || !level.hasChunkAt(pos) || level.getBlockState(pos).is(AEBlocks.MYSTERIOUS_CUBE.block());
    }

    private record CachedResult(@Nullable BlockPos closestMeteoritePos, long received) {}
}
