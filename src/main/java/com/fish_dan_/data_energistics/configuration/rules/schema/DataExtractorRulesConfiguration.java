package com.fish_dan_.data_energistics.configuration.rules.schema;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.io.StrictYamlReader;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.Slot;
import com.fish_dan_.data_energistics.configuration.rules.DefaultRuleValues;
import com.fish_dan_.data_energistics.configuration.rules.LoadedRules;
import com.fish_dan_.data_energistics.configuration.rules.codec.DataExtractorRuleEntries;
import com.fish_dan_.data_energistics.configuration.rules.io.DataExtractorRulesYamlStore;
import com.fish_dan_.data_energistics.configuration.rules.io.DataExtractorRulesYamlStore.LoadedRuleConfiguration;
import com.fish_dan_.data_energistics.configuration.rules.io.DataExtractorRulesYamlStore.PreparedRules;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;
import org.jetbrains.annotations.ApiStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Defines the native, independently watched Data Extractor rule Configuration. */
@Config(
        id = DataExtractorRulesConfiguration.CONFIG_ID,
        filename = DataExtractorRulesConfiguration.FILENAME,
        group = Data_Energistics.MODID)
public final class DataExtractorRulesConfiguration {

    public static final String CONFIG_ID = Data_Energistics.MODID + "_data_extractor_rules";
    public static final String FILENAME = Data_Energistics.MODID + "/data_extractor_rules";

    public static volatile DataExtractorRulesConfiguration INSTANCE;

    @ApiStatus.Internal
    public static ConfigHolder<DataExtractorRulesConfiguration> INTERNAL_INSTANCE;

    private static Path source;
    private static RuleSource publishedSource;
    private static String lastRejection = "";
    private static boolean initialized;

    @Configurable(key = Configurable.LocalizationKey.FULL)
    @Configurable.Comment({
            "Carrier rule columns. Values at the same array index form one row.",
            "载体规则列；各数组中相同索引的值组成一行。"
    })
    public CarrierRuleSchema carrierRules = new CarrierRuleSchema();

    @Configurable(key = Configurable.LocalizationKey.FULL)
    @Configurable.Comment({
            "Output rule columns. Values at the same array index form one output stack row.",
            "输出规则列；各数组中相同索引的值组成一行输出。"
    })
    public OutputRuleSchema outputRules = new OutputRuleSchema();

    private volatile LoadedRules loadedRules;

    /** Registers the standalone YAML and exposes the framework-owned schema instance directly. */
    public static synchronized void init(Path configRoot, DefaultRuleValues defaults) throws IOException {
        if (initialized) {
            throw new IllegalStateException("Data Extractor rule configuration is already initialized");
        }

        PreparedRules prepared = DataExtractorRulesYamlStore.prepare(configRoot, defaults);
        ConfigHolder<DataExtractorRulesConfiguration> internal = Configuration.registerConfig(
                DataExtractorRulesConfiguration.class,
                ConfigFormats.YAML);
        synchronized (internal.getLock()) {
            StrictYamlReader.readInto(prepared.target(), internal);
            DataExtractorRulesConfiguration configuration = internal.getConfigInstance();
            LoadedRules loadedByConfiguration = DataExtractorRuleEntries.compile(
                    configuration.carrierRules,
                    configuration.outputRules,
                    prepared.target());
            if (!loadedByConfiguration.equals(prepared.rules())) {
                throw new IllegalStateException(
                        "Configuration loaded Data Extractor rules that differ from the prevalidated YAML");
            }
            attach(internal, prepared.target(), loadedByConfiguration);
        }
        Data_Energistics.LOGGER.info(
                "Loaded Data Extractor rule YAML {}{}{}",
                prepared.target(),
                prepared.importedLegacyJson() ? " after importing legacy JSON" : "",
                prepared.recoveredTemporary() ? " after recovering a migration temporary" : "");
    }

    /** Returns the complete immutable rules currently used by gameplay. */
    public LoadedRules rules() {
        return this.loadedRules;
    }

    /** Applies watched rule arrays on the server thread without requiring a restart. */
    public static void refresh() {
        RuleSource before;
        synchronized (INTERNAL_INSTANCE.getLock()) {
            before = capture(INTERNAL_INSTANCE.getConfigInstance());
            if (before.equals(publishedSource)) {
                return;
            }
        }

        LoadedRuleConfiguration strictCandidate;
        try {
            strictCandidate = DataExtractorRulesYamlStore.read(source);
        } catch (IOException | RuntimeException exception) {
            reject(exception.toString(), exception);
            return;
        }

        RuleSource yamlCandidate;
        synchronized (strictCandidate.holder().getLock()) {
            yamlCandidate = capture(strictCandidate.holder().getConfigInstance());
        }

        synchronized (INTERNAL_INSTANCE.getLock()) {
            DataExtractorRulesConfiguration current = INTERNAL_INSTANCE.getConfigInstance();
            RuleSource after = capture(current);
            if (!before.equals(after)) {
                return;
            }
            if (!before.equals(yamlCandidate)) {
                reject("Holder and strict rule YAML disagree");
                return;
            }
            current.loadedRules = strictCandidate.rules();
            INSTANCE = current;
            publishedSource = after;
            lastRejection = "";
        }
        Data_Energistics.LOGGER.info(
                "Published Data Extractor rule YAML {} with fingerprint {}",
                source,
                before.hashCode());
    }

    static synchronized void attach(
                                    ConfigHolder<DataExtractorRulesConfiguration> internal,
                                    Path target,
                                    LoadedRules initialRules) {
        if (initialized) {
            throw new IllegalStateException("Data Extractor rule configuration is already initialized");
        }
        synchronized (internal.getLock()) {
            DataExtractorRulesConfiguration configuration = internal.getConfigInstance();
            configuration.loadedRules = initialRules;
            INTERNAL_INSTANCE = internal;
            INSTANCE = configuration;
            source = target;
            publishedSource = capture(configuration);
            initialized = true;
        }
    }

    private static RuleSource capture(DataExtractorRulesConfiguration configuration) {
        CarrierRuleSchema carriers = configuration.carrierRules;
        OutputRuleSchema outputs = configuration.outputRules;
        return new RuleSource(
                new CarrierSource(
                        objectValues(carriers.slots),
                        objectValues(carriers.dataTypes),
                        objectValues(carriers.inputItems),
                        objectValues(carriers.recordedItems),
                        floatValues(carriers.progressPerItems),
                        floatValues(carriers.requiredAmounts)),
                new OutputSource(
                        objectValues(outputs.dataTypes),
                        objectValues(outputs.recordedItems),
                        objectValues(outputs.items),
                        integerValues(outputs.counts)));
    }

    private static <T> List<T> objectValues(T[] values) {
        return Arrays.asList(values.clone());
    }

    private static List<Float> floatValues(float[] values) {
        List<Float> copy = new ArrayList<>(values.length);
        for (float value : values) {
            copy.add(value);
        }
        return List.copyOf(copy);
    }

    private static List<Integer> integerValues(int[] values) {
        return Arrays.stream(values).boxed().toList();
    }

    private static void reject(String reason) {
        if (reason.equals(lastRejection)) {
            return;
        }
        lastRejection = reason;
        Data_Energistics.LOGGER.error(
                "Rejected runtime Data Extractor rule YAML {}; gameplay keeps the previous rule instance: {}",
                source,
                reason);
    }

    private static void reject(String reason, Throwable cause) {
        if (reason.equals(lastRejection)) {
            return;
        }
        lastRejection = reason;
        Data_Energistics.LOGGER.error(
                "Rejected runtime Data Extractor rule YAML {}; gameplay keeps the previous rule instance: {}",
                source,
                reason,
                cause);
    }

    private record RuleSource(CarrierSource carrierRules, OutputSource outputRules) {}

    private record CarrierSource(
                                 List<Slot> slots,
                                 List<DataType> dataTypes,
                                 List<String> inputItems,
                                 List<String> recordedItems,
                                 List<Float> progressPerItems,
                                 List<Float> requiredAmounts) {}

    private record OutputSource(
                                List<DataType> dataTypes,
                                List<String> recordedItems,
                                List<String> items,
                                List<Integer> counts) {}
}
