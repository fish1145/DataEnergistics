package com.fish_dan_.data_energistics.configuration;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.config.DataExtractorRuleTable;
import com.fish_dan_.data_energistics.configuration.LegacyTomlImporter.PreparedConfiguration;
import com.fish_dan_.data_energistics.configuration.rules.DefaultRuleValues;

import net.neoforged.fml.loading.FMLPaths;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.format.ConfigFormats;
import dev.toma.configuration.config.value.ConfigValue;

import java.io.IOException;
import java.nio.file.Path;

/** Enforces prevalidation, registration, first snapshot publication and rule loading in one fixed order. */
public final class ConfigurationBootstrap {

    private static State state = State.NEW;
    private static ConfigHolder<DataEnergisticsConfiguration> registeredHolder;

    private ConfigurationBootstrap() {}

    public static synchronized void initialize() {
        if (state != State.NEW) {
            throw new IllegalStateException("Configuration bootstrap cannot run from state " + state);
        }
        state = State.INITIALIZING;
        Path configRoot = FMLPaths.CONFIGDIR.get();
        try {
            PreparedConfiguration prepared = LegacyTomlImporter.prepare(configRoot);
            ConfigHolder<DataEnergisticsConfiguration> holder = Configuration.registerConfig(
                    DataEnergisticsConfiguration.class,
                    ConfigFormats.YAML);
            ConfigurationSnapshot initial;
            synchronized (holder.getLock()) {
                StrictYamlReader.readInto(prepared.target(), holder);
                int activePlannerThreads = activeInteger(holder, "trinityCrafting.plannerThreads");
                int activePlannerQueueCapacity = activeInteger(holder, "trinityCrafting.plannerQueueCapacity");
                initial = SnapshotAssembler.assemble(
                        holder.getConfigInstance(),
                        prepared.target(),
                        1L,
                        activePlannerThreads,
                        activePlannerQueueCapacity);
            }
            ConfigurationRuntime.publish(initial);
            DefaultRuleValues ruleDefaults = new DefaultRuleValues(
                    holder.getConfigInstance().dataExtractor.cropInputMappings,
                    initial.dataExtractor().cropRequiredAmount(),
                    initial.dataExtractor().oreRequiredAmount());
            DataExtractorRuleTable.load(
                    configRoot.resolve("data_energistics-data_extractor_rules.json"),
                    ruleDefaults);
            registeredHolder = holder;
            state = State.READY;
            Data_Energistics.LOGGER.info(
                    "Loaded Configuration YAML {} at revision {}{}{}",
                    prepared.target(),
                    initial.revision(),
                    prepared.importedLegacyFiles() ? " after importing legacy TOML" : "",
                    prepared.recoveredTemporary() ? " after recovering a migration temporary" : "");
        } catch (IOException | RuntimeException exception) {
            state = State.FAILED;
            Data_Energistics.LOGGER.fatal("Configuration bootstrap failed; startup cannot continue", exception);
            throw new IllegalStateException("Configuration bootstrap failed", exception);
        }
    }

    public static synchronized ConfigHolder<DataEnergisticsConfiguration> holder() {
        if (state != State.READY) {
            throw new IllegalStateException("Configuration holder is unavailable in state " + state);
        }
        return registeredHolder;
    }

    private static int activeInteger(ConfigHolder<DataEnergisticsConfiguration> holder, String path) {
        ConfigValue<?> value = (ConfigValue<?>) holder.getConfigValue(path, Integer.class)
                .orElseThrow(() -> new IllegalStateException("Missing restart-restricted field " + path));
        return (Integer) value.getActiveValue();
    }

    private enum State {
        NEW,
        INITIALIZING,
        READY,
        FAILED
    }
}
