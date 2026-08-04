package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
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

    private static final long TARGET_TICK_NANOS = 50_000_000L;
    private static final long OVERLOADED_TRICKLE_NANOS = 1_000_000L;
    private static final CraftingServerDispatchBudgetImpl SERVER_DISPATCH_BUDGET = new CraftingServerDispatchBudgetImpl(
            System::nanoTime,
            TARGET_TICK_NANOS,
            OVERLOADED_TRICKLE_NANOS);
    private static volatile MinecraftServer sampledServer;
    private static volatile long lastCompletedNanos;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerTickPre(ServerTickEvent.Pre event) {
        sampledServer = event.getServer();
        SERVER_DISPATCH_BUDGET.beginTick();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long[] tickTimes = server.getTickTimesNanos();
        if (tickTimes.length == 0) {
            throw new IllegalStateException("Minecraft server tick ring is empty");
        }
        sampledServer = server;
        lastCompletedNanos = tickTimes[Math.floorMod(server.getTickCount(), tickTimes.length)];
        SERVER_DISPATCH_BUDGET.completeTick(lastCompletedNanos);
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        if (sampledServer == event.getServer()) {
            sampledServer = null;
            lastCompletedNanos = 0L;
            SERVER_DISPATCH_BUDGET.reset();
        }
    }

    /**
     * @return last completed tick duration for this server, or zero before the first complete tick
     */
    public static long lastCompletedNanos(MinecraftServer server) {
        return sampledServer == server ? lastCompletedNanos : 0L;
    }

    /**
     * @return server-wide dispatch budget for this logical server, or an unbounded boundary before sampling starts
     */
    public static CraftingServerDispatchBudget dispatchBudget(MinecraftServer server) {
        return sampledServer == server ? SERVER_DISPATCH_BUDGET : CraftingServerDispatchBudget.unbounded();
    }
}
