package com.fish_dan_.data_energistics.configuration.rules.schema;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.rules.DefaultRuleValues;
import com.fish_dan_.data_energistics.configuration.rules.LoadedRules;
import com.fish_dan_.data_energistics.configuration.rules.RuleFormatException;
import com.fish_dan_.data_energistics.configuration.rules.codec.DataExtractorRuleEntries;
import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;
import dev.toma.configuration.config.io.ConfigIO;

/** Direct Configuration-owned schema for the independently stored Data Extractor rule YAML. */
@Config(
        id = DataExtractorRulesConfiguration.CONFIG_ID,
        filename = DataExtractorRulesConfiguration.FILENAME,
        group = Data_Energistics.MODID)
public final class DataExtractorRulesConfiguration {

    public static final String CONFIG_ID = Data_Energistics.MODID + "_data_extractor_rules";
    public static final String FILENAME = Data_Energistics.MODID + "/data_extractor_rules";

    public static final ConfigHolder<DataExtractorRulesConfiguration> HOLDER = Configuration.registerConfig(DataExtractorRulesConfiguration.class, ConfigFormats.YAML);
    public static final DataExtractorRulesConfiguration INSTANCE = HOLDER.getConfigInstance();

    @Configurable
    @Configurable.Comment({
            "Carrier rule columns. Values at the same array index form one row.",
            "载体规则列；各数组中相同索引的值组成一行。"
    })
    public CarrierRuleSchema carrierRules = new CarrierRuleSchema(defaultRuleValues());

    @Configurable
    @Configurable.Comment({
            "Output rule columns. Values at the same array index form one output stack row.",
            "输出规则列；各数组中相同索引的值组成一行输出。"
    })
    public OutputRuleSchema outputRules = new OutputRuleSchema();

    private DataExtractorRulesConfiguration() {}

    /** Compiles the current native arrays directly; Configuration's Auto-Sync updates the source arrays directly. */
    public LoadedRules rules() {
        try {
            return DataExtractorRuleEntries.compile(
                    this.carrierRules,
                    this.outputRules,
                    ConfigIO.getConfigFile(HOLDER).toPath());
        } catch (RuleFormatException exception) {
            throw new IllegalStateException("Data Extractor rule configuration is invalid", exception);
        }
    }

    private static DefaultRuleValues defaultRuleValues() {
        return new DefaultRuleValues(
                DefaultRuleValues.builtInCropRules(),
                (float) DataEnergisticsConfiguration.INSTANCE.machines.dataExtractor.cropRequiredAmount,
                (float) DataEnergisticsConfiguration.INSTANCE.machines.dataExtractor.oreRequiredAmount);
    }
}
