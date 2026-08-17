package com.fish_dan_.data_energistics.configuration.client;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

import dev.toma.configuration.client.ConfigurationClient;
import dev.toma.configuration.config.adapter.TypeMatcher;

/** Adds client-only presentation behavior to the Configuration screen. */
public final class ConfigurationClientRegistrar {

    private ConfigurationClientRegistrar() {}

    public static void register() {
        ConfigurationClient.setCustomConfigTheme(
                DataEnergisticsConfiguration.HOLDER,
                theme -> theme.registerDisplayAdapter(
                        TypeMatcher.matchEnum(),
                        new CraftingQuantityModeDisplayAdapter()));
    }
}
