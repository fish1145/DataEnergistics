package com.fish_dan_.data_energistics.bootstrap.client;

import com.fish_dan_.data_energistics.client.ModKeyMappings;

import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

final class ClientKeyMappingRegistrar {

    private ClientKeyMappingRegistrar() {}

    static void register(RegisterKeyMappingsEvent event) {
        event.register(ModKeyMappings.OPEN_PATTERN_PROVIDER);
        event.register(ModKeyMappings.RENAME_PATTERN_PROVIDER);
        event.register(ModKeyMappings.TOGGLE_DIGITAL_STORAGE_DEPOT_BUCKET_MODE);
    }
}
