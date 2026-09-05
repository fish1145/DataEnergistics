package com.fish_dan_.data_energistics.bootstrap.common;

import com.fish_dan_.data_energistics.blockentity.machine.DataTeleportAnchorBlockEntity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.lifecycle.TrinityDispatchProposalLifecycle;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningGatewayLifecycle;
import com.fish_dan_.data_energistics.common.entrypoint.provider.PatternProviderRuntimeBindings;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.orbital.control.OrbitalControlActionDispatcher;
import com.fish_dan_.data_energistics.orbital.control.OrbitalOwnershipActionDispatcher;
import com.fish_dan_.data_energistics.recipe.charger.DataIntegratedChargerPatternModeResolver;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import org.jspecify.annotations.Nullable;

public final class ServerLifecycleEventHandler {

    private static volatile @Nullable MinecraftServer stoppingServer;

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
            try {
                OrbitalControlActionDispatcher.clearPreviews(event.getServer());
            } finally {
                OrbitalOwnershipActionDispatcher.clear(event.getServer());
            }
            PatternProviderRuntimeBindings.clearLiveBindings();
        } finally {
            try {
                DataIntegratedChargerPatternModeResolver.clearCache();
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
    }

    public static boolean isStopping(MinecraftServer server) {
        return stoppingServer == server;
    }
}
