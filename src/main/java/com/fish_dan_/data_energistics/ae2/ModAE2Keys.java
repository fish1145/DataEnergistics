package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.client.DataFlowKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKeyType;
import net.neoforged.neoforge.registries.RegisterEvent;

public final class ModAE2Keys {
    private static boolean clientRegistered;

    private ModAE2Keys() {
    }

    public static void register(RegisterEvent event) {
        event.register(AEKeyType.REGISTRY_KEY, DataFlowKeyType.TYPE.getId(), () -> DataFlowKeyType.TYPE);
    }

    public static void registerClient() {
        if (clientRegistered) {
            return;
        }

        clientRegistered = true;
        AEKeyRendering.register(DataFlowKeyType.TYPE, DataFlowKey.class, new DataFlowKeyRenderHandler());
    }
}
