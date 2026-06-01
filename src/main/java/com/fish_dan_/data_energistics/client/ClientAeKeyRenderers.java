package com.fish_dan_.data_energistics.client;

import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataFlowKeyType;
import com.fish_dan_.data_energistics.ae2.DataKey;
import com.fish_dan_.data_energistics.ae2.DataKeyType;
import com.fish_dan_.data_energistics.util.ReflectionAccess;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import java.lang.invoke.VarHandle;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public final class ClientAeKeyRenderers {

    private static boolean registered;
    private static final DataFlowKeyRenderHandler DATA_FLOW_RENDER_HANDLER = new DataFlowKeyRenderHandler();
    private static final DataKeyRenderHandler DATA_RENDER_HANDLER = new DataKeyRenderHandler();
    private static final Optional<VarHandle> RENDERERS_FIELD = ReflectionAccess.findStaticField(AEKeyRendering.class, "renderers");

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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void overwrite(AEKeyType type, AEKeyRenderHandler<?> handler) {
        try {
            Map<AEKeyType, AEKeyRenderHandler<?>> current = (Map<AEKeyType, AEKeyRenderHandler<?>>) ReflectionAccess.getField(RENDERERS_FIELD, null);
            if (RENDERERS_FIELD.isEmpty() || current == null) {
                throw new IllegalStateException("AE key render handler registry is unavailable");
            }
            Map<AEKeyType, AEKeyRenderHandler<?>> updated = new IdentityHashMap<>(current);
            updated.put(type, (AEKeyRenderHandler<? extends AEKey>) handler);
            RENDERERS_FIELD.get().set(updated);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to override AE key render handler for " + type, e);
        }
    }
}
