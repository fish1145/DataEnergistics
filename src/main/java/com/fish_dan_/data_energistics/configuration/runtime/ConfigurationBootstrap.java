package com.fish_dan_.data_energistics.configuration.runtime;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.rules.schema.DataExtractorRulesConfiguration;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.configuration.snapshot.ConfigurationSnapshot;
import com.fish_dan_.data_energistics.configuration.snapshot.SnapshotAssembler;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.format.ConfigFormats;
import dev.toma.configuration.config.io.ConfigIO;

import java.io.IOException;
import java.nio.file.Path;

/** Enforces prevalidation, registration, first snapshot publication and rule loading in one fixed order. */
public final class ConfigurationBootstrap {

    private static State state = State.NEW;

    private ConfigurationBootstrap() {}

    public static synchronized HolderFingerprintBridge initialize() {
        if (state != State.NEW) {
            throw new IllegalStateException("Configuration bootstrap cannot run from state " + state);
        }
        state = State.INITIALIZING;
        try {
            ConfigHolder<DataEnergisticsConfiguration> holder = Configuration.registerConfig(
                    DataEnergisticsConfiguration.class,
                    ConfigFormats.YAML);
            Path source = ConfigIO.getConfigFile(holder).toPath();
            ConfigurationSnapshot initial;
            synchronized (holder.getLock()) {
                initial = SnapshotAssembler.assemble(
                        holder.getConfigInstance(),
                        source,
                        1L);
            }
            DataEnergisticsConfiguration.initialize(holder, initial);
            DataExtractorRulesConfiguration.init();
            state = State.READY;
            Data_Energistics.LOGGER.info(
                    "Loaded Configuration YAML {} at revision {}",
                    source,
                    initial.revision());
            return new HolderFingerprintBridge(holder, source);
        } catch (IOException | RuntimeException exception) {
            state = State.FAILED;
            Data_Energistics.LOGGER.fatal("Configuration bootstrap failed; startup cannot continue", exception);
            throw new IllegalStateException("Configuration bootstrap failed", exception);
        }
    }

    private enum State {
        NEW,
        INITIALIZING,
        READY,
        FAILED
    }
}
