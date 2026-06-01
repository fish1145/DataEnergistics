package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class UniversalTerminalStateSyncHandler {

    private static final String HANDLER_CLASS = "com.fish_dan_.data_energistics.client.screen.UniversalTerminalStateSyncClientHandler";

    private UniversalTerminalStateSyncHandler() {}

    public static void cacheSyncedTerminalState(UniversalTerminalStateSyncPayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        ReflectionAccess.invokeStatic(
                HANDLER_CLASS,
                "cacheSyncedTerminalState",
                new Class<?>[] { UniversalTerminalStateSyncPayload.class },
                payload);
    }
}
