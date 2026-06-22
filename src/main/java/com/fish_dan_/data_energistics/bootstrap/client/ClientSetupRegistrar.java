package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.integration.CuriosDollRendererRegistry;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.registry.ModStorageCells;

final class ClientSetupRegistrar {

    private ClientSetupRegistrar() {}

    static void register() {
        ClientAeKeyRendererRegistrar.register();
        ModStorageCells.registerClientModels();
        ClientRenderLayerRegistrar.register();
        ClientItemModelPropertyRegistrar.register();
        if (ModFlags.isCuriosLoaded()) {
            CuriosDollRendererRegistry.register();
        }
        ClientGameEventRegistrar.register();
    }
}
