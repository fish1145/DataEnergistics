package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Captures the last complete logical-server tick from Minecraft's monotonic tick ring.
 *
 * <p>
 * Minecraft records the current complete sample immediately before {@link ServerTickEvent.Post}. Grid dispatch
 * therefore consumes that sample on the next tick. The sample is diagnostic input only and is never a correctness
 * deadline.
 * </p>
 */
public final class TrinityServerTickMetrics {

    private static volatile MinecraftServer sampledServer;
    private static volatile long lastCompletedNanos;

    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long[] tickTimes = server.getTickTimesNanos();
        if (tickTimes.length == 0) {
            throw new IllegalStateException("Minecraft server tick ring is empty");
        }
        sampledServer = server;
        lastCompletedNanos = tickTimes[Math.floorMod(server.getTickCount(), tickTimes.length)];
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        if (sampledServer == event.getServer()) {
            sampledServer = null;
            lastCompletedNanos = 0L;
        }
    }

    /**
     * @return last completed tick duration for this server, or zero before the first complete tick
     */
    public static long lastCompletedNanos(MinecraftServer server) {
        return sampledServer == server ? lastCompletedNanos : 0L;
    }
}
