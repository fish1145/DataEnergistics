package com.fish_dan_.data_energistics.configuration;

/** Selects the sole production configuration source while the migration branch crosses its atomic cutover. */
public final class GameplayConfiguration {

    private static volatile Source source = Source.LEGACY;

    private GameplayConfiguration() {}

    public static DataEnergisticsSettings current() {
        return switch (source) {
            case LEGACY -> LegacyConfigBridge.current();
            case CONFIGURATION -> ConfigurationRuntime.current();
        };
    }

    static void activateConfigurationSnapshots() {
        source = Source.CONFIGURATION;
    }

    private enum Source {
        LEGACY,
        CONFIGURATION
    }
}
