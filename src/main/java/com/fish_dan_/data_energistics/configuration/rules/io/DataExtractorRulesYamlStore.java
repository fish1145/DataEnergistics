package com.fish_dan_.data_energistics.configuration.rules.io;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.configuration.io.ConfigurationYamlStore;
import com.fish_dan_.data_energistics.configuration.io.StrictYamlReader;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleMigrator;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.DataType;
import com.fish_dan_.data_energistics.configuration.rules.DataExtractorRuleTable.Slot;
import com.fish_dan_.data_energistics.configuration.rules.DefaultRuleValues;
import com.fish_dan_.data_energistics.configuration.rules.LoadedRules;
import com.fish_dan_.data_energistics.configuration.rules.codec.DataExtractorRuleEntries;
import com.fish_dan_.data_energistics.configuration.rules.codec.DataExtractorRuleEntries.CarrierArrays;
import com.fish_dan_.data_energistics.configuration.rules.codec.DataExtractorRuleEntries.OutputArrays;
import com.fish_dan_.data_energistics.configuration.rules.schema.DataExtractorRulesConfiguration;
import com.fish_dan_.data_energistics.configuration.validation.InvalidConfigurationException;

import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.format.ConfigFormats;
import dev.toma.configuration.config.format.IConfigFormat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;

/** Owns strict loading and one-time legacy JSON import for the native rule Configuration YAML. */
public final class DataExtractorRulesYamlStore {

    public static final String LEGACY_JSON_FILENAME = Data_Energistics.MODID + "-data_extractor_rules.json";

    private DataExtractorRulesYamlStore() {}

    public static PreparedRules prepare(Path configRoot, DefaultRuleValues defaults) throws IOException {
        Path target = target(configRoot);
        if (Files.exists(target)) {
            return new PreparedRules(target, read(target).rules(), false, false);
        }

        List<Path> temporaries = migrationTemporaries(target);
        if (temporaries.size() > 1) {
            throw new IOException("Multiple Data Extractor rule migration temporaries require manual review: " + temporaries);
        }
        if (temporaries.size() == 1) {
            LoadedRuleConfiguration recovered = recoverTemporary(temporaries.getFirst(), target);
            return new PreparedRules(target, recovered.rules(), false, true);
        }

        Path legacyJson = configRoot.resolve(LEGACY_JSON_FILENAME);
        boolean importedLegacyJson = Files.exists(legacyJson);
        LoadedRules initial = DataExtractorRuleMigrator.loadLegacyOrDefaults(legacyJson, defaults);
        ConfigHolder<DataExtractorRulesConfiguration> holder = newHolder();
        CarrierArrays carriers = DataExtractorRuleEntries.encodeCarriers(initial);
        ConfigurationYamlStore.forceValue(holder, "carrierRules.slots", Slot[].class, carriers.slots());
        ConfigurationYamlStore.forceValue(
                holder,
                "carrierRules.dataTypes",
                DataType[].class,
                carriers.dataTypes());
        ConfigurationYamlStore.forceValue(
                holder,
                "carrierRules.inputItems",
                String[].class,
                carriers.inputItems());
        ConfigurationYamlStore.forceValue(
                holder,
                "carrierRules.recordedItems",
                String[].class,
                carriers.recordedItems());
        ConfigurationYamlStore.forceValue(
                holder,
                "carrierRules.progressPerItems",
                Float[].class,
                carriers.progressPerItems());
        ConfigurationYamlStore.forceValue(
                holder,
                "carrierRules.requiredAmounts",
                Float[].class,
                carriers.requiredAmounts());

        OutputArrays outputs = DataExtractorRuleEntries.encodeOutputs(initial);
        ConfigurationYamlStore.forceValue(
                holder,
                "outputRules.dataTypes",
                DataType[].class,
                outputs.dataTypes());
        ConfigurationYamlStore.forceValue(
                holder,
                "outputRules.recordedItems",
                String[].class,
                outputs.recordedItems());
        ConfigurationYamlStore.forceValue(holder, "outputRules.items", String[].class, outputs.items());
        ConfigurationYamlStore.forceValue(holder, "outputRules.counts", Integer[].class, outputs.counts());
        LoadedRuleConfiguration written = writeAtomically(holder, target);
        return new PreparedRules(target, written.rules(), importedLegacyJson, false);
    }

    public static Path target(Path configRoot) {
        return configRoot.resolve(DataExtractorRulesConfiguration.FILENAME + ".yaml");
    }

    /** Reports whether startup must generate native rows instead of loading an existing rule source. */
    public static boolean requiresGeneratedDefaults(Path configRoot) throws IOException {
        Path target = target(configRoot);
        if (Files.exists(target) || Files.exists(configRoot.resolve(LEGACY_JSON_FILENAME))) {
            return false;
        }
        return migrationTemporaries(target).isEmpty();
    }

    public static ConfigHolder<DataExtractorRulesConfiguration> newHolder() {
        return new ConfigHolder<>(
                DataExtractorRulesConfiguration.class,
                DataExtractorRulesConfiguration.CONFIG_ID,
                DataExtractorRulesConfiguration.FILENAME,
                Data_Energistics.MODID,
                ConfigFormats.YAML);
    }

    public static LoadedRuleConfiguration read(Path source) throws IOException {
        ConfigHolder<DataExtractorRulesConfiguration> holder = newHolder();
        StrictYamlReader.YamlDocument document = StrictYamlReader.readInto(source, holder);
        DataExtractorRulesConfiguration schema = holder.getConfigInstance();
        LoadedRules rules = DataExtractorRuleEntries.compile(schema.carrierRules, schema.outputRules, source);
        return new LoadedRuleConfiguration(holder, rules, document);
    }

    private static LoadedRuleConfiguration writeAtomically(
                                                           ConfigHolder<DataExtractorRulesConfiguration> holder,
                                                           Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "." + target.getFileName() + ".migration.", ".tmp");
        try {
            IConfigFormat format = holder.getFormat().createFormat();
            holder.values().forEach(value -> value.serializeValue(format));
            format.writeFile(temporary.toFile());
            forceFile(temporary);
            verifyUtf8WithoutBom(temporary);
            LoadedRuleConfiguration validated = read(temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException(
                        "Atomic move is not supported for Data Extractor rule migration temporary " + temporary,
                        exception);
            }
            return validated;
        } catch (IOException | RuntimeException exception) {
            throw new IOException(
                    "Failed to publish Data Extractor rule YAML; migration evidence remains at " + temporary,
                    exception);
        }
    }

    private static LoadedRuleConfiguration recoverTemporary(Path temporary, Path target) throws IOException {
        LoadedRuleConfiguration validated = read(temporary);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic move is not supported while recovering " + temporary, exception);
        }
        return validated;
    }

    private static List<Path> migrationTemporaries(Path target) throws IOException {
        Path directory = target.getParent();
        if (Files.notExists(directory)) {
            return List.of();
        }
        String prefix = "." + target.getFileName() + ".migration.";
        try (var entries = Files.list(directory)) {
            return entries
                    .filter(path -> {
                        String filename = path.getFileName().toString();
                        return filename.startsWith(prefix) && filename.endsWith(".tmp");
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    private static void forceFile(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void verifyUtf8WithoutBom(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            throw new InvalidConfigurationException(path, "$", "UTF-8 BOM is not allowed", "EF BB BF");
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
        } catch (CharacterCodingException exception) {
            throw new InvalidConfigurationException(
                    path,
                    "$",
                    "generated rule YAML is not valid UTF-8",
                    exception.getMessage(),
                    exception);
        }
    }

    public record PreparedRules(
                                Path target,
                                LoadedRules rules,
                                boolean importedLegacyJson,
                                boolean recoveredTemporary) {}

    public record LoadedRuleConfiguration(
                                          ConfigHolder<DataExtractorRulesConfiguration> holder,
                                          LoadedRules rules,
                                          StrictYamlReader.YamlDocument document) {}
}
