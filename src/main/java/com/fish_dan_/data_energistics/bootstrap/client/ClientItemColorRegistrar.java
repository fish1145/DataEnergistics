package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.DEItemColors;

import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

final class ClientItemColorRegistrar {

    private ClientItemColorRegistrar() {}

    static void register(RegisterColorHandlersEvent.Item event) {
        DEItemColors.register(event);
    }
}
