package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.DEKeyMappings;

import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

final class ClientKeyMappingRegistrar {

    private ClientKeyMappingRegistrar() {}

    static void register(RegisterKeyMappingsEvent event) {
        event.register(DEKeyMappings.OPEN_PATTERN_PROVIDER);
        event.register(DEKeyMappings.RENAME_PATTERN_PROVIDER);
        event.register(DEKeyMappings.TOGGLE_DIGITAL_STORAGE_DEPOT_BUCKET_MODE);
    }
}
