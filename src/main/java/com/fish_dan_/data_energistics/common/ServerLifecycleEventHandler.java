package com.fish_dan_.data_energistics.common;

import com.fish_dan_.data_energistics.blockentity.DataTeleportAnchorBlockEntity;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public final class ServerLifecycleEventHandler {

    private static volatile MinecraftServer stoppingServer;

    ServerLifecycleEventHandler() {}

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        stoppingServer = null;
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        stoppingServer = event.getServer();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        try {
            DataTeleportAnchorBlockEntity.clearRuntimeAnchorCache();
        } finally {
            if (stoppingServer == event.getServer()) {
                stoppingServer = null;
            }
        }
    }

    public static boolean isStopping(MinecraftServer server) {
        return server != null && stoppingServer == server;
    }
}
