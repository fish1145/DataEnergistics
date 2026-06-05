package com.fish_dan_.data_energistics.client;

import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataFlowKeyType;
import com.fish_dan_.data_energistics.ae2.DataKey;
import com.fish_dan_.data_energistics.ae2.DataKeyType;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import java.util.IdentityHashMap;
import java.util.Map;

public final class ClientAeKeyRenderers {

    private static boolean registered;
    private static final DataFlowKeyRenderHandler DATA_FLOW_RENDER_HANDLER = new DataFlowKeyRenderHandler();
    private static final DataKeyRenderHandler DATA_RENDER_HANDLER = new DataKeyRenderHandler();

    private ClientAeKeyRenderers() {}

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;
        AEKeyRendering.register(DataFlowKeyType.TYPE, DataFlowKey.class, DATA_FLOW_RENDER_HANDLER);
        AEKeyRendering.register(DataKeyType.TYPE, DataKey.class, DATA_RENDER_HANDLER);
    }

    public static void reregister() {
        overwrite(DataFlowKeyType.TYPE, DATA_FLOW_RENDER_HANDLER);
        overwrite(DataKeyType.TYPE, DATA_RENDER_HANDLER);
        registered = true;
    }

    private static void overwrite(AEKeyType type, AEKeyRenderHandler<?> handler) {
        Map<AEKeyType, AEKeyRenderHandler<?>> updated = new IdentityHashMap<>(AEKeyRendering.renderers);
        updated.put(type, (AEKeyRenderHandler<? extends AEKey>) handler);
        AEKeyRendering.renderers = updated;
    }
}
