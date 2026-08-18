package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.integration.curios.client.CuriosDollRendererRegistry;
import com.fish_dan_.data_energistics.configuration.client.ConfigurationClientRegistrar;
import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.registry.DEStorageCells;

final class ClientSetupRegistrar {

    private ClientSetupRegistrar() {}

    static void register() {
        ClientAeKeyRendererRegistrar.register();
        ConfigurationClientRegistrar.register();
        DEStorageCells.registerClientModels();
        ClientRenderLayerRegistrar.register();
        ClientItemModelPropertyRegistrar.register();
        if (ModFlags.isCuriosLoaded()) {
            CuriosDollRendererRegistry.register();
        }
        ClientGameEventRegistrar.register();
    }
}
