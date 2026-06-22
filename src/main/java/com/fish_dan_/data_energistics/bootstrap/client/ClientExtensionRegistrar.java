package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.ModFluidClientExtensions;

import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

final class ClientExtensionRegistrar {

    private ClientExtensionRegistrar() {}

    static void register(RegisterClientExtensionsEvent event) {
        ModFluidClientExtensions.register(event);
    }
}
