package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.ClientAeKeyRenderers;

final class ClientAeKeyRendererRegistrar {

    private ClientAeKeyRendererRegistrar() {}

    static void register() {
        ClientAeKeyRenderers.register();
    }

    static void reregister() {
        ClientAeKeyRenderers.reregister();
    }
}
