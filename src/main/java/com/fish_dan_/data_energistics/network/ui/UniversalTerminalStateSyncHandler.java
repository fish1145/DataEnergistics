package com.fish_dan_.data_energistics.network.ui;

import com.fish_dan_.data_energistics.bridge.DataEnergisticsClientBridgeAccess;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class UniversalTerminalStateSyncHandler {

    private UniversalTerminalStateSyncHandler() {}

    public static void cacheSyncedTerminalState(UniversalTerminalStateSyncPayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        DataEnergisticsClientBridgeAccess.get().cacheSyncedTerminalState(payload);
    }
}
