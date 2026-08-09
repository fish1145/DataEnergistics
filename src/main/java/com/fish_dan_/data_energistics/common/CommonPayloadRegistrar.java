package com.fish_dan_.data_energistics.common;

import com.fish_dan_.data_energistics.network.DEPayloads;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

final class CommonPayloadRegistrar {

    private CommonPayloadRegistrar() {}

    static void register(RegisterPayloadHandlersEvent event) {
        DEPayloads.register(event);
    }
}
