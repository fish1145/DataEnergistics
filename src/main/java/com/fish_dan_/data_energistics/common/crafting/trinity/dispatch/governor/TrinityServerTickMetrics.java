package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.governor;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server.CraftingDispatchCompletion;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server.CraftingDispatchParticipant;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.server.TrinityServerDispatchScheduler;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Captures the last complete logical-server tick from Minecraft's monotonic tick ring.
 *
 * <p>
 * Minecraft records the current complete sample immediately before {@link ServerTickEvent.Post}. The LOWEST Post
 * handler captures that sample, runs the central Grid rotation, and then completes the shared server budget. The
 * sample is diagnostic input only and is never a correctness deadline.
 * </p>
 */
public final class TrinityServerTickMetrics {

    private static final long TARGET_TICK_NANOS = 50_000_000L;
    private static final long OVERLOADED_TRICKLE_NANOS = 1_000_000L;
    private static final MeasuredCraftingServerDispatchBudget SERVER_DISPATCH_BUDGET = new MeasuredCraftingServerDispatchBudget(
            System::nanoTime,
            TARGET_TICK_NANOS,
            OVERLOADED_TRICKLE_NANOS);
    private static final TrinityServerDispatchScheduler SERVER_DISPATCH_SCHEDULER = TrinityServerDispatchScheduler.create();
    private static volatile MinecraftServer sampledServer;
    private static volatile long lastCompletedNanos;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onServerTickPre(ServerTickEvent.Pre event) {
        sampledServer = event.getServer();
        SERVER_DISPATCH_BUDGET.beginTick();
        SERVER_DISPATCH_SCHEDULER.beginTick();
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
        try {
            SERVER_DISPATCH_SCHEDULER.dispatchTick();
        } finally {
            SERVER_DISPATCH_BUDGET.completeTick(lastCompletedNanos);
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        if (sampledServer == event.getServer()) {
            sampledServer = null;
            lastCompletedNanos = 0L;
            SERVER_DISPATCH_SCHEDULER.reset();
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

    /**
     * Registers one prepared AE Grid for the current server-wide physical-call rotation.
     *
     * @param server      logical server that owns the Grid
     * @param participant prepared one-tick Grid dispatch participant
     */
    public static void registerDispatchParticipant(MinecraftServer server,
                                                   CraftingDispatchParticipant participant) {
        if (sampledServer != server) {
            throw new IllegalStateException("Trinity dispatch participant belongs to an inactive logical server");
        }
        SERVER_DISPATCH_SCHEDULER.register(participant);
    }

    /**
     * Registers one completion-only Grid for the current server-wide rotation without physical dispatch state.
     *
     * @param server     logical server that owns the Grid
     * @param completion prepared completion boundary
     */
    public static void registerDispatchCompletion(MinecraftServer server,
                                                  CraftingDispatchCompletion completion) {
        if (sampledServer != server) {
            throw new IllegalStateException("Trinity dispatch completion belongs to an inactive logical server");
        }
        SERVER_DISPATCH_SCHEDULER.registerCompletion(completion);
    }
}
