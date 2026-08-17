package com.fish_dan_.data_energistics.bootstrap.common;

import com.fish_dan_.data_energistics.blockentity.machine.DataTeleportAnchorBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.lifecycle.TrinityDispatchProposalLifecycle;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningGatewayLifecycle;
import com.fish_dan_.data_energistics.common.entrypoint.provider.PatternProviderRuntimeBindings;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

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
        TrinityPlanningGatewayLifecycle.start(DataEnergisticsConfiguration.INSTANCE.trinity.crafting);
        try {
            TrinityDispatchProposalLifecycle.start();
        } catch (RuntimeException exception) {
            TrinityPlanningGatewayLifecycle.stop();
            throw exception;
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        stoppingServer = event.getServer();
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        try {
            PatternProviderRuntimeBindings.clearLiveBindings();
        } finally {
            try {
                DataTeleportAnchorBlockEntity.clearRuntimeAnchorCache();
            } finally {
                try {
                    TrinityDispatchProposalLifecycle.stop();
                } finally {
                    try {
                        TrinityPlanningGatewayLifecycle.stop();
                    } finally {
                        if (stoppingServer == event.getServer()) {
                            stoppingServer = null;
                        }
                    }
                }
            }
        }
    }

    public static boolean isStopping(MinecraftServer server) {
        return server != null && stoppingServer == server;
    }
}
