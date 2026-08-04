package com.fish_dan_.data_energistics.configuration;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.configuration.ConfigurationYamlStore.LoadedConfiguration;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import dev.toma.configuration.config.ConfigHolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Imports the fixed 64-field legacy TOML inventory without modifying any source file. */
public final class LegacyTomlImporter {

    private static final int LEGACY_BINDING_VARIANTS = 512;
    private static final int MIGRATED_BINDING_VARIANTS = 32768;

    private static final List<LegacyFile> FILES = List.of(
            legacyFile(
                    "data_energistics-common.toml",
                    field("dataRipperBaseCost", "dataRipper.baseCost", ValueKind.INTEGER),
                    field("dataRipperBlacklist", "dataRipper.blacklist", ValueKind.STRING_ARRAY),
                    field("dataRipperMultipliers", "dataRipper.multipliers", ValueKind.STRING_ARRAY),
                    field("dataDistributionTowerRange", "dataDistributionTower.range", ValueKind.INTEGER),
                    field(
                            "dataSanctumInterfaceItemLimit",
                            "dataSanctumInterface.itemLimit",
                            ValueKind.INTEGER),
                    field(
                            "dataSanctumInterfaceFluidBuckets",
                            "dataSanctumInterface.fluidBuckets",
                            ValueKind.INTEGER),
                    field(
                            "dataSanctumInterfaceReturnItemLimit",
                            "dataSanctumInterface.returnItemLimit",
                            ValueKind.INTEGER),
                    field(
                            "dataSanctumInterfaceReturnFluidBuckets",
                            "dataSanctumInterface.returnFluidBuckets",
                            ValueKind.INTEGER)),
            legacyFile(
                    "data_energistics-data_extractor.toml",
                    field("baseDamage", "dataExtractor.baseDamage", ValueKind.INTEGER),
                    field("workIntervalSeconds", "dataExtractor.workIntervalSeconds", ValueKind.INTEGER),
                    field("baseDataFlowPerCycle", "dataExtractor.baseDataFlowPerCycle", ValueKind.INTEGER),
                    field("dataFlowPerSwordDamage", "dataExtractor.dataFlowPerSwordDamage", ValueKind.INTEGER),
                    field("baseTargetLimit", "dataExtractor.baseTargetLimit", ValueKind.INTEGER),
                    field(
                            "targetLimitPerCapacityCard",
                            "dataExtractor.targetLimitPerCapacityCard",
                            ValueKind.INTEGER),
                    field(
                            "extraTargetDataFlowMultiplier",
                            "dataExtractor.extraTargetDataFlowMultiplier",
                            ValueKind.DECIMAL),
                    field("mobRequiredDamage", "dataExtractor.mobRequiredDamage", ValueKind.DECIMAL),
                    field("mobDataBlacklist", "dataExtractor.mobDataBlacklist", ValueKind.STRING),
                    field("oreRequiredAmount", "dataExtractor.oreRequiredAmount", ValueKind.DECIMAL),
                    field("oreDataBlacklist", "dataExtractor.oreDataBlacklist", ValueKind.STRING),
                    field("cropRequiredAmount", "dataExtractor.cropRequiredAmount", ValueKind.DECIMAL),
                    field("cropDataBlacklist", "dataExtractor.cropDataBlacklist", ValueKind.STRING),
                    field("cropDataWhitelist", "dataExtractor.cropDataWhitelist", ValueKind.STRING),
                    field("cropInputMappings", "dataExtractor.cropInputMappings", ValueKind.STRING)),
            legacyFile(
                    "data_energistics-tnt.toml",
                    field(
                            "flatteningTnt.tntConfigurable.clearChunkRadius",
                            "flatteningTnt.tntConfigurable.clearChunkRadius",
                            ValueKind.INTEGER),
                    field(
                            "flatteningTnt.tntConfigurable.clearStartYOffset",
                            "flatteningTnt.tntConfigurable.clearStartYOffset",
                            ValueKind.INTEGER),
                    field(
                            "flatteningTnt.tntConfigurable.clearHeight",
                            "flatteningTnt.tntConfigurable.clearHeight",
                            ValueKind.INTEGER),
                    field(
                            "flatteningTnt.tntConfigurable.fillChunkRadius",
                            "flatteningTnt.tntConfigurable.fillChunkRadius",
                            ValueKind.INTEGER),
                    field(
                            "flatteningTnt.tntConfigurable.fillYOffset",
                            "flatteningTnt.tntConfigurable.fillYOffset",
                            ValueKind.INTEGER),
                    field(
                            "flatteningTnt.tntConfigurable.fillBlock",
                            "flatteningTnt.tntConfigurable.fillBlock",
                            ValueKind.STRING),
                    field(
                            "flatteningTnt.tntConfigurable.centerOffsetX",
                            "flatteningTnt.tntConfigurable.centerOffsetX",
                            ValueKind.INTEGER),
                    field(
                            "flatteningTnt.tntConfigurable.centerOffsetY",
                            "flatteningTnt.tntConfigurable.centerOffsetY",
                            ValueKind.INTEGER),
                    field(
                            "flatteningTnt.tntConfigurable.centerOffsetZ",
                            "flatteningTnt.tntConfigurable.centerOffsetZ",
                            ValueKind.INTEGER),
                    field(
                            "flatteningTnt.tntConfigurable.preserveFluids",
                            "flatteningTnt.tntConfigurable.preserveFluids",
                            ValueKind.BOOLEAN),
                    field(
                            "flatteningTnt.tntConfigurable.replaceUnbreakableBlocks",
                            "flatteningTnt.tntConfigurable.replaceUnbreakableBlocks",
                            ValueKind.BOOLEAN),
                    discardedField(
                            "flatteningTnt.tntConfigurable.displayName",
                            ValueKind.STRING,
                            Compatibility.DISCARD_DISPLAY_NAME),
                    field(
                            "flatteningTnt.dataNuke.workIntervalTicks",
                            "flatteningTnt.dataNuke.workIntervalTicks",
                            ValueKind.INTEGER),
                    field(
                            "flatteningTnt.dataNuke.maxRadius",
                            "flatteningTnt.dataNuke.maxRadius",
                            ValueKind.INTEGER),
                    field(
                            "flatteningTnt.dataNuke.centerEntityConsumeRadius",
                            "flatteningTnt.dataNuke.centerEntityConsumeRadius",
                            ValueKind.DECIMAL)),
            legacyFile(
                    "data_energistics-solar_panel.toml",
                    field("dayGenerationAEPerTick", "solarPanel.dayGenerationAEPerTick", ValueKind.DECIMAL),
                    field("nightGenerationAEPerTick", "solarPanel.nightGenerationAEPerTick", ValueKind.DECIMAL),
                    field("speedCardBonusRatio", "solarPanel.speedCardBonusRatio", ValueKind.DECIMAL),
                    field("energyCardCapacityBonusAE", "solarPanel.energyCardCapacityBonusAE", ValueKind.DECIMAL)),
            legacyFile(
                    "data_energistics-trinity_crafting.toml",
                    field("maxSccKeys", "trinityCrafting.maxSccKeys", ValueKind.INTEGER),
                    compatibleField(
                            "maxBindingVariants",
                            "trinityCrafting.maxBindingVariants",
                            ValueKind.INTEGER,
                            Compatibility.UPGRADE_BINDING_VARIANTS),
                    field("maxScheduleStates", "trinityCrafting.maxScheduleStates", ValueKind.INTEGER),
                    field("graphRebuildBudgetMs", "trinityCrafting.graphRebuildBudgetMs", ValueKind.INTEGER),
                    field("plannerThreads", "trinityCrafting.plannerThreads", ValueKind.INTEGER),
                    field("plannerQueueCapacity", "trinityCrafting.plannerQueueCapacity", ValueKind.INTEGER),
                    field("dynamicRetryMaxTicks", "trinityCrafting.dynamicRetryMaxTicks", ValueKind.INTEGER),
                    field("defaultQuantityMode", "trinityCrafting.defaultQuantityMode", ValueKind.QUANTITY_MODE),
                    discardedField("mipTimeoutMs", ValueKind.INTEGER, Compatibility.IGNORE_MIP_TIMEOUT)),
            legacyFile(
                    "data_energistics-trinity_dispatch.toml",
                    field("hardGridAttempts", "trinityDispatch.hardGridAttempts", ValueKind.INTEGER),
                    field("hardProviderAttempts", "trinityDispatch.hardProviderAttempts", ValueKind.INTEGER),
                    field("hardCommitBudgetMs", "trinityDispatch.hardCommitBudgetMs", ValueKind.INTEGER),
                    field("safeGridAttempts", "trinityDispatch.safeGridAttempts", ValueKind.INTEGER),
                    field("safeProviderAttempts", "trinityDispatch.safeProviderAttempts", ValueKind.INTEGER),
                    field("safeCommitBudgetMs", "trinityDispatch.safeCommitBudgetMs", ValueKind.INTEGER),
                    field("safeActorPermits", "trinityDispatch.safeActorPermits", ValueKind.INTEGER),
                    field("warmupTicks", "trinityDispatch.warmupTicks", ValueKind.INTEGER),
                    field("metricsWindowTicks", "trinityDispatch.metricsWindowTicks", ValueKind.INTEGER),
                    field("ewmaAlpha", "trinityDispatch.ewmaAlpha", ValueKind.DECIMAL),
                    field("transitionWindows", "trinityDispatch.transitionWindows", ValueKind.INTEGER),
                    field("cooldownTicks", "trinityDispatch.cooldownTicks", ValueKind.INTEGER),
                    field("safeHoldTicks", "trinityDispatch.safeHoldTicks", ValueKind.INTEGER)));

    private LegacyTomlImporter() {}

    public static PreparedConfiguration prepare(Path configRoot) throws IOException {
        Path target = ConfigurationYamlStore.target(configRoot);
        if (Files.exists(target)) {
            return new PreparedConfiguration(target, ConfigurationYamlStore.read(target, 0L), false, false);
        }

        List<Path> temporaries = ConfigurationYamlStore.migrationTemporaries(target);
        if (temporaries.size() > 1) {
            throw invalid(
                    target,
                    "$",
                    "multiple migration temporary files make recovery ambiguous",
                    temporaries.toString());
        }
        if (temporaries.size() == 1) {
            Path temporary = temporaries.getFirst();
            LoadedConfiguration recovered = ConfigurationYamlStore.recoverTemporary(temporary, target, 0L);
            return new PreparedConfiguration(target, recovered, false, true);
        }

        ConfigHolder<DataEnergisticsConfiguration> holder = ConfigurationYamlStore.newHolder();
        boolean imported = false;
        for (LegacyFile legacyFile : FILES) {
            Path source = configRoot.resolve(legacyFile.filename());
            if (Files.exists(source)) {
                imported = true;
                importFile(source, legacyFile, holder);
            }
        }
        SnapshotAssembler.assemble(holder.getConfigInstance(), target, 0L);
        LoadedConfiguration loaded = ConfigurationYamlStore.writeAtomically(holder, target, 0L);
        return new PreparedConfiguration(target, loaded, imported, false);
    }

    public static int recognizedLegacyFieldCount() {
        return FILES.stream().mapToInt(file -> file.fields().size()).sum();
    }

    private static void importFile(
                                   Path source,
                                   LegacyFile legacyFile,
                                   ConfigHolder<DataEnergisticsConfiguration> holder) throws IOException {
        Map<String, Object> values;
        try (CommentedFileConfig config = CommentedFileConfig.builder(source).sync().build()) {
            config.load();
            values = flatten(config);
        } catch (RuntimeException exception) {
            throw invalid(source, "$", "TOML syntax is invalid", exception.getMessage(), exception);
        }

        for (Map.Entry<String, Object> entry : values.entrySet()) {
            LegacyField field = legacyFile.fields().get(entry.getKey());
            if (field == null) {
                throw invalid(source, entry.getKey(), "unknown legacy TOML field", externalText(entry.getValue()));
            }
            apply(source, field, entry.getValue(), holder);
        }
    }

    private static Map<String, Object> flatten(UnmodifiableConfig config) throws InvalidConfigurationException {
        Map<String, Object> flattened = new LinkedHashMap<>();
        flatten(config, "", flattened);
        return flattened;
    }

    private static void flatten(
                                UnmodifiableConfig config,
                                String prefix,
                                Map<String, Object> flattened) throws InvalidConfigurationException {
        for (Map.Entry<String, Object> entry : config.valueMap().entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof UnmodifiableConfig child) {
                flatten(child, path, flattened);
            } else if (flattened.putIfAbsent(path, value) != null) {
                throw new InvalidConfigurationException(Path.of("<legacy-toml>"), path, "duplicate TOML key", path);
            }
        }
    }

    private static void apply(
                              Path source,
                              LegacyField field,
                              Object value,
                              ConfigHolder<DataEnergisticsConfiguration> holder) throws InvalidConfigurationException {
        Object parsed = parse(source, field, value);
        switch (field.compatibility()) {
            case DISCARD_DISPLAY_NAME -> {
                Data_Energistics.LOGGER.warn(
                        "Discarding legacy {} from {}; use localization key block.data_energistics.tnt_configurable",
                        field.legacyPath(),
                        source);
                return;
            }
            case IGNORE_MIP_TIMEOUT -> {
                Data_Energistics.LOGGER.info("Ignoring retired legacy setting {} from {}", field.legacyPath(), source);
                return;
            }
            case UPGRADE_BINDING_VARIANTS -> {
                int configured = (Integer) parsed;
                if (configured == LEGACY_BINDING_VARIANTS) {
                    parsed = MIGRATED_BINDING_VARIANTS;
                    Data_Energistics.LOGGER.info(
                            "Migrated legacy Trinity maxBindingVariants from {} to {}",
                            LEGACY_BINDING_VARIANTS,
                            MIGRATED_BINDING_VARIANTS);
                }
            }
            case DIRECT -> {}
        }
        Object accepted = force(holder, field.targetPath(), field.kind(), parsed);
        if (!sameValue(parsed, accepted)) {
            throw invalid(
                    source,
                    field.legacyPath(),
                    "value is outside the target schema constraints at " + field.targetPath(),
                    externalText(value));
        }
    }

    private static Object parse(Path source, LegacyField field, Object value) throws InvalidConfigurationException {
        String path = field.legacyPath();
        return switch (field.kind()) {
            case INTEGER -> integer(source, path, value);
            case DECIMAL -> decimal(source, path, value);
            case STRING -> string(source, path, value);
            case STRING_ARRAY -> stringArray(source, path, value);
            case BOOLEAN -> bool(source, path, value);
            case QUANTITY_MODE -> quantityMode(source, path, value);
        };
    }

    private static Object force(
                                ConfigHolder<DataEnergisticsConfiguration> holder,
                                String targetPath,
                                ValueKind kind,
                                Object value) {
        return switch (kind) {
            case INTEGER -> ConfigurationYamlStore.forceValue(holder, targetPath, Integer.class, (Integer) value);
            case DECIMAL -> ConfigurationYamlStore.forceValue(holder, targetPath, Double.class, (Double) value);
            case STRING -> ConfigurationYamlStore.forceValue(holder, targetPath, String.class, (String) value);
            case STRING_ARRAY -> ConfigurationYamlStore.forceValue(
                    holder,
                    targetPath,
                    String[].class,
                    (String[]) value);
            case BOOLEAN -> ConfigurationYamlStore.forceValue(holder, targetPath, Boolean.class, (Boolean) value);
            case QUANTITY_MODE -> ConfigurationYamlStore.forceValue(
                    holder,
                    targetPath,
                    CraftingQuantityMode.class,
                    (CraftingQuantityMode) value);
        };
    }

    private static boolean sameValue(Object requested, Object accepted) {
        if (requested instanceof String[] requestedArray && accepted instanceof String[] acceptedArray) {
            return Arrays.equals(requestedArray, acceptedArray);
        }
        return requested.equals(accepted);
    }

    private static int integer(Path source, String path, Object value) throws InvalidConfigurationException {
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            throw invalid(source, path, "expected a TOML integer", externalText(value));
        }
        long number = ((Number) value).longValue();
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw invalid(source, path, "integer does not fit in 32 bits", Long.toString(number));
        }
        return (int) number;
    }

    private static double decimal(Path source, String path, Object value) throws InvalidConfigurationException {
        if (!(value instanceof Number number)) {
            throw invalid(source, path, "expected a TOML number", externalText(value));
        }
        return number.doubleValue();
    }

    private static String string(Path source, String path, Object value) throws InvalidConfigurationException {
        if (!(value instanceof String text)) {
            throw invalid(source, path, "expected a TOML string", externalText(value));
        }
        return text;
    }

    private static String[] stringArray(Path source, String path, Object value) throws InvalidConfigurationException {
        if (!(value instanceof List<?> list)) {
            throw invalid(source, path, "expected a TOML string array", externalText(value));
        }
        List<String> strings = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            Object entry = list.get(index);
            if (!(entry instanceof String text)) {
                throw invalid(
                        source,
                        path + "[" + index + "]",
                        "array entry must be a string",
                        externalText(entry));
            }
            strings.add(text);
        }
        return strings.toArray(String[]::new);
    }

    private static boolean bool(Path source, String path, Object value) throws InvalidConfigurationException {
        if (!(value instanceof Boolean flag)) {
            throw invalid(source, path, "expected a TOML boolean", externalText(value));
        }
        return flag;
    }

    private static CraftingQuantityMode quantityMode(Path source, String path, Object value)
                                                                                             throws InvalidConfigurationException {
        String name = string(source, path, value);
        try {
            return CraftingQuantityMode.valueOf(name);
        } catch (IllegalArgumentException exception) {
            throw invalid(source, path, "unknown CraftingQuantityMode", name, exception);
        }
    }

    private static String externalText(Object value) {
        return value == null ? "null" : value.toString();
    }

    private static LegacyFile legacyFile(String filename, LegacyField... fields) {
        Map<String, LegacyField> byPath = new LinkedHashMap<>();
        for (LegacyField field : fields) {
            if (byPath.putIfAbsent(field.legacyPath(), field) != null) {
                throw new IllegalStateException("Duplicate legacy descriptor " + filename + ":" + field.legacyPath());
            }
        }
        return new LegacyFile(filename, Map.copyOf(byPath));
    }

    private static LegacyField field(String legacyPath, String targetPath, ValueKind kind) {
        return compatibleField(legacyPath, targetPath, kind, Compatibility.DIRECT);
    }

    private static LegacyField compatibleField(
                                               String legacyPath,
                                               String targetPath,
                                               ValueKind kind,
                                               Compatibility compatibility) {
        return new LegacyField(legacyPath, targetPath, kind, compatibility);
    }

    private static LegacyField discardedField(
                                              String legacyPath,
                                              ValueKind kind,
                                              Compatibility compatibility) {
        return new LegacyField(legacyPath, "", kind, compatibility);
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

    public record PreparedConfiguration(
                                        Path target,
                                        LoadedConfiguration loaded,
                                        boolean importedLegacyFiles,
                                        boolean recoveredTemporary) {}

    private record LegacyFile(String filename, Map<String, LegacyField> fields) {}

    private record LegacyField(
                               String legacyPath,
                               String targetPath,
                               ValueKind kind,
                               Compatibility compatibility) {}

    private enum ValueKind {
        INTEGER,
        DECIMAL,
        STRING,
        STRING_ARRAY,
        BOOLEAN,
        QUANTITY_MODE
    }

    private enum Compatibility {
        DIRECT,
        DISCARD_DISPLAY_NAME,
        IGNORE_MIP_TIMEOUT,
        UPGRADE_BINDING_VARIANTS
    }
}
