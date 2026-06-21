package com.fish_dan_.data_energistics.common;

import com.fish_dan_.data_energistics.blockentity.DataTeleportAnchorBlockEntity;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

final class ServerLifecycleEventHandler {

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        DataTeleportAnchorBlockEntity.clearRuntimeAnchorCache();
    }
}
