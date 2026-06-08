package com.fish_dan_.data_energistics.util;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.Queue;

@SuppressWarnings("resource")
public final class ServerTickDelayQueue {

    private static final Queue<Entry> QUEUE = new ArrayDeque<>();

    public static void runNextServerTick(MinecraftServer server, Runnable task) {
        if (server == null || task == null) {
            return;
        }

        synchronized (QUEUE) {
            QUEUE.add(new Entry(server, task));
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        Queue<Entry> ready = new ArrayDeque<>();
        synchronized (QUEUE) {
            int pendingCount = QUEUE.size();
            for (int i = 0; i < pendingCount; i++) {
                Entry entry = QUEUE.poll();
                if (entry == null) {
                    break;
                }
                if (entry.server() == event.getServer()) {
                    ready.add(entry);
                } else {
                    QUEUE.add(entry);
                }
            }
        }

        for (Entry entry : ready) {
            entry.task().run();
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        synchronized (QUEUE) {
            QUEUE.removeIf(entry -> entry.server() == event.getServer());
        }
    }

    private record Entry(MinecraftServer server, Runnable task) {}
}
