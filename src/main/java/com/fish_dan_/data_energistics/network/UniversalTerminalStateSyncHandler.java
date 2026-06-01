package com.fish_dan_.data_energistics.network;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class UniversalTerminalStateSyncHandler {

    private static final String HANDLER_CLASS = "com.fish_dan_.data_energistics.client.screen.UniversalTerminalStateSyncClientHandler";

    private UniversalTerminalStateSyncHandler() {
    }

    public static void cacheSyncedTerminalState(UniversalTerminalStateSyncPayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        try {
            Class.forName(HANDLER_CLASS)
                    .getMethod("cacheSyncedTerminalState", UniversalTerminalStateSyncPayload.class)
                    .invoke(null, payload);
        } catch (ReflectiveOperationException ignored) {
            // Client cache is a UI optimization; menu state remains authoritative.
        }
    }
}
