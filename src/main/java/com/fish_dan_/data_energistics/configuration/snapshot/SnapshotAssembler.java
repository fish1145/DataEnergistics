package com.fish_dan_.data_energistics.configuration.snapshot;

import com.fish_dan_.data_energistics.configuration.schema.DataEnergisticsConfiguration;
import com.fish_dan_.data_energistics.configuration.snapshot.ConfigurationSnapshot.DataDistributionTowerSettings;
import com.fish_dan_.data_energistics.configuration.snapshot.ConfigurationSnapshot.DataExtractorSettings;
import com.fish_dan_.data_energistics.configuration.snapshot.ConfigurationSnapshot.DataNukeSettings;
import com.fish_dan_.data_energistics.configuration.snapshot.ConfigurationSnapshot.DataRipperSettings;
import com.fish_dan_.data_energistics.configuration.snapshot.ConfigurationSnapshot.DataSanctumInterfaceSettings;
import com.fish_dan_.data_energistics.configuration.snapshot.ConfigurationSnapshot.FlatteningTntSettings;
import com.fish_dan_.data_energistics.configuration.snapshot.ConfigurationSnapshot.SolarPanelSettings;
import com.fish_dan_.data_energistics.configuration.validation.InvalidConfigurationException;
import com.fish_dan_.data_energistics.util.DataRipperConfigParsingUtils.MultiplierEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Converts all mutable schema values into one strictly validated immutable business snapshot.
 */
public final class SnapshotAssembler {

    private static final int MAX_SANCTUM_BASE_CAPACITY = Integer.MAX_VALUE / 8;

    private SnapshotAssembler() {}

    public static ConfigurationSnapshot assemble(
                                                 DataEnergisticsConfiguration schema,
                                                 Path source,
                                                 long revision) throws InvalidConfigurationException {
        return assemble(
                schema,
                source,
                revision,
                schema.trinityCrafting.plannerThreads,
                schema.trinityCrafting.cpuPlannerThreads,
                schema.trinityCrafting.plannerQueueCapacity);
    }

    public static ConfigurationSnapshot assemble(
                                                 DataEnergisticsConfiguration schema,
                                                 Path source,
                                                 long revision,
                                                 int activePlannerThreads,
                                                 int activeCpuPlannerThreads,
                                                 int activePlannerQueueCapacity) throws InvalidConfigurationException {
        DataRipperSettings dataRipper = dataRipper(schema, source);
        DataDistributionTowerSettings tower = tower(schema, source);
        DataSanctumInterfaceSettings sanctum = sanctum(schema, source);
        DataExtractorSettings extractor = extractor(schema, source);
        FlatteningTntSettings tnt = tnt(schema, source);
        DataNukeSettings dataNuke = dataNuke(schema, source);
        SolarPanelSettings solar = solar(schema, source);
        TrinityCraftingSettings crafting = crafting(
                schema,
                source,
                activePlannerThreads,
                activeCpuPlannerThreads,
                activePlannerQueueCapacity);
        TrinityDispatchSettings dispatch = dispatch(schema, source);
        return new ConfigurationSnapshot(
                revision,
                schema.verboseRuntimeLogging,
                dataRipper,
                tower,
                sanctum,
                extractor,
                tnt,
                dataNuke,
                solar,
                crafting,
                dispatch);
    }

    private static DataRipperSettings dataRipper(
                                                 DataEnergisticsConfiguration schema,
                                                 Path source) throws InvalidConfigurationException {
        int baseCost = integer(source, "dataRipper.baseCost", schema.dataRipper.baseCost, 1, Integer.MAX_VALUE);
        List<String> blacklistText = copyExternalArray(source, "dataRipper.blacklist", schema.dataRipper.blacklist);
        List<Pattern> blacklist = new ArrayList<>(blacklistText.size());
        for (int index = 0; index < blacklistText.size(); index++) {
            String entry = blacklistText.get(index);
            String path = "dataRipper.blacklist[" + index + "]";
            if (entry.isBlank()) {
                throw invalid(source, path, "regex must not be blank", entry);
            }
            blacklist.add(pattern(source, path, entry));
        }

        List<String> multiplierPatterns = copyExternalArray(
                source,
                "dataRipper.multipliers.patterns",
                schema.dataRipper.multipliers.patterns);
        double[] multiplierValues = schema.dataRipper.multipliers.values.clone();
        if (multiplierPatterns.size() != multiplierValues.length) {
            throw invalid(
                    source,
                    "dataRipper.multipliers",
                    "patterns and values must have the same length",
                    "patterns=" + multiplierPatterns.size() + ", values=" + multiplierValues.length);
        }
        List<MultiplierEntry> multipliers = new ArrayList<>(multiplierPatterns.size());
        for (int index = 0; index < multiplierPatterns.size(); index++) {
            String patternText = multiplierPatterns.get(index).trim();
            String path = "dataRipper.multipliers[" + index + "]";
            if (patternText.isEmpty()) {
                throw invalid(source, path + ".pattern", "regex must not be blank", patternText);
            }
            double multiplier = multiplierValues[index];
            positiveFinite(source, path, multiplier);
            multipliers.add(new MultiplierEntry(pattern(source, path + ".pattern", patternText), multiplier));
        }
        return new DataRipperSettings(baseCost, blacklist, multipliers);
    }

    private static DataDistributionTowerSettings tower(
                                                       DataEnergisticsConfiguration schema,
                                                       Path source) throws InvalidConfigurationException {
        return new DataDistributionTowerSettings(integer(
                source,
                "dataDistributionTower.range",
                schema.dataDistributionTower.range,
                1,
                128));
    }

    private static DataSanctumInterfaceSettings sanctum(
                                                        DataEnergisticsConfiguration schema,
                                                        Path source) throws InvalidConfigurationException {
        return new DataSanctumInterfaceSettings(
                integer(
                        source,
                        "dataSanctumInterface.itemLimit",
                        schema.dataSanctumInterface.itemLimit,
                        1,
                        MAX_SANCTUM_BASE_CAPACITY),
                integer(
                        source,
                        "dataSanctumInterface.fluidBuckets",
                        schema.dataSanctumInterface.fluidBuckets,
                        1,
                        MAX_SANCTUM_BASE_CAPACITY),
                integer(
                        source,
                        "dataSanctumInterface.returnItemLimit",
                        schema.dataSanctumInterface.returnItemLimit,
                        1,
                        MAX_SANCTUM_BASE_CAPACITY),
                integer(
                        source,
                        "dataSanctumInterface.returnFluidBuckets",
                        schema.dataSanctumInterface.returnFluidBuckets,
                        1,
                        MAX_SANCTUM_BASE_CAPACITY));
    }

    private static DataExtractorSettings extractor(
                                                   DataEnergisticsConfiguration schema,
                                                   Path source) throws InvalidConfigurationException {
        DataEnergisticsConfiguration.DataExtractorSchema extractor = schema.dataExtractor;
        return new DataExtractorSettings(
                integer(source, "dataExtractor.baseDamage", extractor.baseDamage, 0, Integer.MAX_VALUE),
                integer(
                        source,
                        "dataExtractor.workIntervalSeconds",
                        extractor.workIntervalSeconds,
                        1,
                        Integer.MAX_VALUE),
                integer(
                        source,
                        "dataExtractor.baseDataFlowPerCycle",
                        extractor.baseDataFlowPerCycle,
                        0,
                        Integer.MAX_VALUE),
                integer(
                        source,
                        "dataExtractor.dataFlowPerSwordDamage",
                        extractor.dataFlowPerSwordDamage,
                        0,
                        Integer.MAX_VALUE),
                integer(
                        source,
                        "dataExtractor.baseTargetLimit",
                        extractor.baseTargetLimit,
                        1,
                        Integer.MAX_VALUE),
                integer(
                        source,
                        "dataExtractor.targetLimitPerCapacityCard",
                        extractor.targetLimitPerCapacityCard,
                        0,
                        Integer.MAX_VALUE),
                finiteRange(
                        source,
                        "dataExtractor.extraTargetDataFlowMultiplier",
                        extractor.extraTargetDataFlowMultiplier,
                        0.0D,
                        Double.MAX_VALUE),
                positiveFloat(source, "dataExtractor.mobRequiredDamage", extractor.mobRequiredDamage),
                ids(source, "dataExtractor.mobDataBlacklist", extractor.mobDataBlacklist),
                positiveFloat(source, "dataExtractor.oreRequiredAmount", extractor.oreRequiredAmount),
                ids(source, "dataExtractor.oreDataBlacklist", extractor.oreDataBlacklist),
                positiveFloat(source, "dataExtractor.cropRequiredAmount", extractor.cropRequiredAmount),
                ids(source, "dataExtractor.cropDataBlacklist", extractor.cropDataBlacklist),
                ids(source, "dataExtractor.cropDataWhitelist", extractor.cropDataWhitelist));
    }

    private static FlatteningTntSettings tnt(
                                             DataEnergisticsConfiguration schema,
                                             Path source) throws InvalidConfigurationException {
        DataEnergisticsConfiguration.ConfigurableTntSchema tnt = schema.flatteningTnt.tntConfigurable;
        ResourceLocation blockId = resourceLocation(source, "flatteningTnt.tntConfigurable.fillBlock", tnt.fillBlock);
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElseThrow(() -> invalid(
                source,
                "flatteningTnt.tntConfigurable.fillBlock",
                "block id is not registered",
                tnt.fillBlock));
        if (block == Blocks.AIR) {
            throw invalid(
                    source,
                    "flatteningTnt.tntConfigurable.fillBlock",
                    "air cannot be used as the floor block",
                    tnt.fillBlock);
        }
        return new FlatteningTntSettings(
                integer(
                        source,
                        "flatteningTnt.tntConfigurable.clearChunkRadius",
                        tnt.clearChunkRadius,
                        0,
                        64),
                integer(
                        source,
                        "flatteningTnt.tntConfigurable.clearStartYOffset",
                        tnt.clearStartYOffset,
                        -384,
                        384),
                integer(
                        source,
                        "flatteningTnt.tntConfigurable.clearHeight",
                        tnt.clearHeight,
                        1,
                        512),
                integer(
                        source,
                        "flatteningTnt.tntConfigurable.fillChunkRadius",
                        tnt.fillChunkRadius,
                        0,
                        64),
                integer(
                        source,
                        "flatteningTnt.tntConfigurable.fillYOffset",
                        tnt.fillYOffset,
                        -384,
                        384),
                block.defaultBlockState(),
                new BlockPos(
                        integer(
                                source,
                                "flatteningTnt.tntConfigurable.centerOffsetX",
                                tnt.centerOffsetX,
                                -512,
                                512),
                        integer(
                                source,
                                "flatteningTnt.tntConfigurable.centerOffsetY",
                                tnt.centerOffsetY,
                                -512,
                                512),
                        integer(
                                source,
                                "flatteningTnt.tntConfigurable.centerOffsetZ",
                                tnt.centerOffsetZ,
                                -512,
                                512)),
                tnt.preserveFluids,
                tnt.replaceUnbreakableBlocks);
    }

    private static DataNukeSettings dataNuke(
                                             DataEnergisticsConfiguration schema,
                                             Path source) throws InvalidConfigurationException {
        DataEnergisticsConfiguration.DataNukeSchema nuke = schema.flatteningTnt.dataNuke;
        return new DataNukeSettings(
                integer(
                        source,
                        "flatteningTnt.dataNuke.workIntervalTicks",
                        nuke.workIntervalTicks,
                        1,
                        1200),
                integer(source, "flatteningTnt.dataNuke.maxRadius", nuke.maxRadius, 1, 8192),
                finiteRange(
                        source,
                        "flatteningTnt.dataNuke.centerEntityConsumeRadius",
                        nuke.centerEntityConsumeRadius,
                        0.0D,
                        128.0D));
    }

    private static SolarPanelSettings solar(
                                            DataEnergisticsConfiguration schema,
                                            Path source) throws InvalidConfigurationException {
        DataEnergisticsConfiguration.SolarPanelSchema solar = schema.solarPanel;
        return new SolarPanelSettings(
                finiteRange(
                        source,
                        "solarPanel.dayGenerationAEPerTick",
                        solar.dayGenerationAEPerTick,
                        0.0D,
                        Double.MAX_VALUE),
                finiteRange(
                        source,
                        "solarPanel.nightGenerationAEPerTick",
                        solar.nightGenerationAEPerTick,
                        0.0D,
                        Double.MAX_VALUE),
                finiteRange(
                        source,
                        "solarPanel.speedCardBonusRatio",
                        solar.speedCardBonusRatio,
                        0.0D,
                        1000.0D),
                finiteRange(
                        source,
                        "solarPanel.energyCardCapacityBonusAE",
                        solar.energyCardCapacityBonusAE,
                        0.0D,
                        Double.MAX_VALUE));
    }

    private static TrinityCraftingSettings crafting(
                                                    DataEnergisticsConfiguration schema,
                                                    Path source,
                                                    int activePlannerThreads,
                                                    int activeCpuPlannerThreads,
                                                    int activePlannerQueueCapacity) throws InvalidConfigurationException {
        DataEnergisticsConfiguration.TrinityCraftingSchema crafting = schema.trinityCrafting;
        if (crafting.defaultQuantityMode == null) {
            throw invalid(
                    source,
                    "trinityCrafting.defaultQuantityMode",
                    "quantity mode is required",
                    "null");
        }
        return new TrinityCraftingSettings(
                integer(source, "trinityCrafting.maxSccKeys", crafting.maxSccKeys, 1, Integer.MAX_VALUE),
                integer(
                        source,
                        "trinityCrafting.maxBindingVariants",
                        crafting.maxBindingVariants,
                        1,
                        Integer.MAX_VALUE),
                integer(
                        source,
                        "trinityCrafting.maxScheduleStates",
                        crafting.maxScheduleStates,
                        1,
                        Integer.MAX_VALUE),
                integer(
                        source,
                        "trinityCrafting.graphRebuildBudgetMs",
                        crafting.graphRebuildBudgetMs,
                        1,
                        Integer.MAX_VALUE),
                integer(source, "trinityCrafting.plannerThreads", activePlannerThreads, 1, 8),
                integer(source, "trinityCrafting.cpuPlannerThreads", activeCpuPlannerThreads, 1, 8),
                integer(
                        source,
                        "trinityCrafting.plannerQueueCapacity",
                        activePlannerQueueCapacity,
                        1,
                        Integer.MAX_VALUE),
                integer(
                        source,
                        "trinityCrafting.dynamicRetryMaxTicks",
                        crafting.dynamicRetryMaxTicks,
                        1,
                        Integer.MAX_VALUE),
                crafting.defaultQuantityMode);
    }

    private static TrinityDispatchSettings dispatch(
                                                    DataEnergisticsConfiguration schema,
                                                    Path source) throws InvalidConfigurationException {
        DataEnergisticsConfiguration.TrinityDispatchSchema dispatch = schema.trinityDispatch;
        int hardGridAttempts = integer(
                source,
                "trinityDispatch.hardGridAttempts",
                dispatch.hardGridAttempts,
                1,
                Integer.MAX_VALUE);
        int hardProviderAttempts = integer(
                source,
                "trinityDispatch.hardProviderAttempts",
                dispatch.hardProviderAttempts,
                1,
                Integer.MAX_VALUE);
        int hardCommitBudgetMs = integer(
                source,
                "trinityDispatch.hardCommitBudgetMs",
                dispatch.hardCommitBudgetMs,
                1,
                Integer.MAX_VALUE);
        int safeGridAttempts = integer(
                source,
                "trinityDispatch.safeGridAttempts",
                dispatch.safeGridAttempts,
                1,
                Integer.MAX_VALUE);
        int safeProviderAttempts = integer(
                source,
                "trinityDispatch.safeProviderAttempts",
                dispatch.safeProviderAttempts,
                1,
                Integer.MAX_VALUE);
        int safeCommitBudgetMs = integer(
                source,
                "trinityDispatch.safeCommitBudgetMs",
                dispatch.safeCommitBudgetMs,
                1,
                Integer.MAX_VALUE);
        if (safeGridAttempts > hardGridAttempts) {
            throw invalid(
                    source,
                    "trinityDispatch.safeGridAttempts",
                    "SAFE grid attempts must not exceed hardGridAttempts=" + hardGridAttempts,
                    Integer.toString(safeGridAttempts));
        }
        if (safeProviderAttempts > hardProviderAttempts) {
            throw invalid(
                    source,
                    "trinityDispatch.safeProviderAttempts",
                    "SAFE provider attempts must not exceed hardProviderAttempts=" + hardProviderAttempts,
                    Integer.toString(safeProviderAttempts));
        }
        if (safeCommitBudgetMs > hardCommitBudgetMs) {
            throw invalid(
                    source,
                    "trinityDispatch.safeCommitBudgetMs",
                    "SAFE commit budget must not exceed hardCommitBudgetMs=" + hardCommitBudgetMs,
                    Integer.toString(safeCommitBudgetMs));
        }
        return new TrinityDispatchSettings(
                hardGridAttempts,
                hardProviderAttempts,
                hardCommitBudgetMs,
                safeGridAttempts,
                safeProviderAttempts,
                safeCommitBudgetMs,
                integer(
                        source,
                        "trinityDispatch.safeActorPermits",
                        dispatch.safeActorPermits,
                        1,
                        Integer.MAX_VALUE),
                integer(
                        source,
                        "trinityDispatch.safeRetryBackoffTicks",
                        dispatch.safeRetryBackoffTicks,
                        1,
                        Integer.MAX_VALUE),
                integer(
                        source,
                        "trinityDispatch.warmupTicks",
                        dispatch.warmupTicks,
                        1,
                        Integer.MAX_VALUE),
                integer(
                        source,
                        "trinityDispatch.metricsWindowTicks",
                        dispatch.metricsWindowTicks,
                        1,
                        Integer.MAX_VALUE),
                finiteRange(source, "trinityDispatch.ewmaAlpha", dispatch.ewmaAlpha, Double.MIN_NORMAL, 1.0D),
                integer(
                        source,
                        "trinityDispatch.transitionWindows",
                        dispatch.transitionWindows,
                        1,
                        Integer.MAX_VALUE),
                integer(
                        source,
                        "trinityDispatch.cooldownTicks",
                        dispatch.cooldownTicks,
                        0,
                        Integer.MAX_VALUE),
                integer(
                        source,
                        "trinityDispatch.safeHoldTicks",
                        dispatch.safeHoldTicks,
                        1,
                        Integer.MAX_VALUE));
    }

    private static List<String> copyExternalArray(Path source, String path, String[] values)
                                                                                             throws InvalidConfigurationException {
        List<String> copy = new ArrayList<>(values.length);
        for (int index = 0; index < values.length; index++) {
            String value = values[index];
            if (value == null) {
                throw invalid(source, path + "[" + index + "]", "array entry must be a string", "null");
            }
            copy.add(value);
        }
        return List.copyOf(copy);
    }

    private static Set<ResourceLocation> ids(Path source, String path, String[] values)
                                                                                        throws InvalidConfigurationException {
        List<String> entries = copyExternalArray(source, path, values);
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            String entry = entries.get(index);
            if (entry.isBlank()) {
                throw invalid(source, path + "[" + index + "]", "resource id must not be blank", entry);
            }
            ResourceLocation id = resourceLocation(source, path + "[" + index + "]", entry);
            if (!ids.add(id)) {
                throw invalid(source, path + "[" + index + "]", "resource id is duplicated", entry);
            }
        }
        return ids;
    }

    private static Pattern pattern(Path source, String path, String value) throws InvalidConfigurationException {
        try {
            return Pattern.compile(value);
        } catch (PatternSyntaxException exception) {
            throw invalid(source, path, "invalid regular expression", value, exception);
        }
    }

    private static ResourceLocation resourceLocation(Path source, String path, String value)
                                                                                             throws InvalidConfigurationException {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw invalid(source, path, "invalid resource location", value);
        }
        return id;
    }

    private static int integer(Path source, String path, int value, int min, int max)
                                                                                      throws InvalidConfigurationException {
        if (value < min || value > max) {
            throw invalid(source, path, "integer must be in [" + min + ", " + max + "]", Integer.toString(value));
        }
        return value;
    }

    private static double finiteRange(Path source, String path, double value, double min, double max)
                                                                                                      throws InvalidConfigurationException {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw invalid(source, path, "number must be finite and in [" + min + ", " + max + "]", Double.toString(value));
        }
        return value;
    }

    private static void positiveFinite(Path source, String path, double value)
                                                                               throws InvalidConfigurationException {
        if (!Double.isFinite(value) || value <= 0.0D) {
            throw invalid(source, path, "number must be finite and positive", Double.toString(value));
        }
    }

    private static float positiveFloat(Path source, String path, double value)
                                                                               throws InvalidConfigurationException {
        if (!Double.isFinite(value) || value <= 0.0D || value > Float.MAX_VALUE) {
            throw invalid(
                    source,
                    path,
                    "number must be finite, positive, and representable as float",
                    Double.toString(value));
        }
        float narrowed = (float) value;
        if (!Float.isFinite(narrowed) || narrowed <= 0.0F) {
            throw invalid(source, path, "number loses validity when narrowed to float", Double.toString(value));
        }
        return narrowed;
    }

    private static double parseDouble(Path source, String path, String value) throws InvalidConfigurationException {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            throw invalid(source, path, "expected a decimal number", value, exception);
        }
    }

    private static InvalidConfigurationException invalid(
                                                         Path source,
                                                         String path,
                                                         String violation,
                                                         String actualValue) {
        return new InvalidConfigurationException(source, path, violation, actualValue);
    }

    private static InvalidConfigurationException invalid(
                                                         Path source,
                                                         String path,
                                                         String violation,
                                                         String actualValue,
                                                         Throwable cause) {
        return new InvalidConfigurationException(source, path, violation, actualValue, cause);
    }
}
