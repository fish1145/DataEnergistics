package com.fish_dan_.data_energistics.client;

import com.fish_dan_.data_energistics.ae2.key.DataKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalizationKey;
import com.fish_dan_.data_energistics.ae2.key.DigitalizationKeyType;
import com.fish_dan_.data_energistics.ae2.key.ManifestBinaryKeyType;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;

import java.util.IdentityHashMap;
import java.util.Map;

public final class ClientAeKeyRenderers {

    private static boolean registered;
    private static final DigitalizationKeyRenderHandler DIGITALIZATION_RENDER_HANDLER = new DigitalizationKeyRenderHandler();
    private static final DataKeyRenderHandler DATA_RENDER_HANDLER = new DataKeyRenderHandler();

    private ClientAeKeyRenderers() {}

    public static void register() {
        if (registered) {
            return;
        }

        registered = true;
        AEKeyRendering.register(DigitalizationKeyType.TYPE, DigitalizationKey.class, DIGITALIZATION_RENDER_HANDLER);
        AEKeyRendering.register(ManifestBinaryKeyType.TYPE, DataKey.class, DATA_RENDER_HANDLER);
    }

    public static void reregister() {
        overwrite(DigitalizationKeyType.TYPE, DIGITALIZATION_RENDER_HANDLER);
        overwrite(ManifestBinaryKeyType.TYPE, DATA_RENDER_HANDLER);
        registered = true;
    }

    private static void overwrite(AEKeyType type, AEKeyRenderHandler<?> handler) {
        Map<AEKeyType, AEKeyRenderHandler<?>> updated = new IdentityHashMap<>(AEKeyRendering.renderers);
        updated.put(type, (AEKeyRenderHandler<? extends AEKey>) handler);
        AEKeyRendering.renderers = updated;
    }
}
