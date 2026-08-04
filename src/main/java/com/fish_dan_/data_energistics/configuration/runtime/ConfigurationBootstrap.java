package com.fish_dan_.data_energistics.configuration.runtime;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.io.StrictYamlReader;
import com.fish_dan_.data_energistics.configuration.migration.LegacyTomlImporter;
import com.fish_dan_.data_energistics.configuration.migration.LegacyTomlImporter.PreparedConfiguration;
import com.fish_dan_.data_energistics.configuration.rules.DefaultRuleValues;
import com.fish_dan_.data_energistics.configuration.rules.DefaultRuleValues.CropRule;
import com.fish_dan_.data_energistics.configuration.rules.io.DataExtractorRulesYamlStore;
import com.fish_dan_.data_energistics.configuration.rules.schema.DataExtractorRulesConfiguration;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.configuration.snapshot.ConfigurationSnapshot;
import com.fish_dan_.data_energistics.configuration.snapshot.SnapshotAssembler;

import net.neoforged.fml.loading.FMLPaths;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.format.ConfigFormats;
import dev.toma.configuration.config.value.ConfigValue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Enforces prevalidation, registration, first snapshot publication and rule loading in one fixed order. */
public final class ConfigurationBootstrap {

    private static State state = State.NEW;

    private ConfigurationBootstrap() {}

    public static synchronized HolderFingerprintBridge initialize() {
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
            List<CropRule> cropRules = DataExtractorRulesYamlStore.requiresGeneratedDefaults(configRoot) ?
                    LegacyTomlImporter.readCropRulesOrDefaults(configRoot) :
                    DefaultRuleValues.builtInCropRules();
            DefaultRuleValues ruleDefaults = new DefaultRuleValues(
                    cropRules,
                    initial.dataExtractor().cropRequiredAmount(),
                    initial.dataExtractor().oreRequiredAmount());
            DataEnergisticsConfiguration.initialize(holder, initial);
            DataExtractorRulesConfiguration.init(configRoot, ruleDefaults);
            state = State.READY;
            Data_Energistics.LOGGER.info(
                    "Loaded Configuration YAML {} at revision {}{}{}",
                    prepared.target(),
                    initial.revision(),
                    prepared.importedLegacyFiles() ? " after importing legacy TOML" : "",
                    prepared.recoveredTemporary() ? " after recovering a migration temporary" : "");
            return new HolderFingerprintBridge(holder, prepared.target());
        } catch (IOException | RuntimeException exception) {
            state = State.FAILED;
            Data_Energistics.LOGGER.fatal("Configuration bootstrap failed; startup cannot continue", exception);
            throw new IllegalStateException("Configuration bootstrap failed", exception);
        }
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
