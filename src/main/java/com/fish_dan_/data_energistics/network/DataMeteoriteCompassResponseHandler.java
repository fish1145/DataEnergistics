package com.fish_dan_.data_energistics.network;

import com.fish_dan_.data_energistics.util.ReflectionAccess;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

public final class DataMeteoriteCompassResponseHandler {

    private static final String HANDLER_CLASS = "com.fish_dan_.data_energistics.client.DataMeteoriteCompassClientCache";

    private DataMeteoriteCompassResponseHandler() {}

    public static void cacheSyncedCompassResult(DataMeteoriteCompassResponsePayload payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        ReflectionAccess.invokeStatic(
                HANDLER_CLASS,
                "cacheSyncedCompassResult",
                new Class<?>[] { DataMeteoriteCompassResponsePayload.class },
                payload);
    }
}
