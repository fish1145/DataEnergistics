package com.fish_dan_.data_energistics.configuration;

import com.fish_dan_.data_energistics.Data_Energistics;

import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.format.ConfigFormats;
import dev.toma.configuration.config.format.IConfigFormat;
import dev.toma.configuration.config.value.ConfigValue;
import dev.toma.configuration.config.value.IConfigValue;

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

/** Reads, validates and atomically creates the single Configuration YAML file. */
public final class ConfigurationYamlStore {

    public static final String RELATIVE_FILENAME = Data_Energistics.MODID + "/" + Data_Energistics.MODID + ".yaml";

    private ConfigurationYamlStore() {}

    public static Path target(Path configRoot) {
        return configRoot.resolve(RELATIVE_FILENAME);
    }

    public static ConfigHolder<DataEnergisticsConfiguration> newHolder() {
        return new ConfigHolder<>(
                DataEnergisticsConfiguration.class,
                Data_Energistics.MODID,
                Data_Energistics.MODID + "/" + Data_Energistics.MODID,
                Data_Energistics.MODID,
                ConfigFormats.YAML);
    }

    public static LoadedConfiguration read(Path source, long revision) throws IOException {
        ConfigHolder<DataEnergisticsConfiguration> holder = newHolder();
        StrictYamlReader.YamlDocument document = StrictYamlReader.readInto(source, holder);
        ConfigurationSnapshot snapshot = SnapshotAssembler.assemble(holder.getConfigInstance(), source, revision);
        return new LoadedConfiguration(holder, snapshot, document);
    }

    public static LoadedConfiguration read(
                                           Path source,
                                           long revision,
                                           int activePlannerThreads,
                                           int activePlannerQueueCapacity) throws IOException {
        ConfigHolder<DataEnergisticsConfiguration> holder = newHolder();
        StrictYamlReader.YamlDocument document = StrictYamlReader.readInto(source, holder);
        ConfigurationSnapshot snapshot = SnapshotAssembler.assemble(
                holder.getConfigInstance(),
                source,
                revision,
                activePlannerThreads,
                activePlannerQueueCapacity);
        return new LoadedConfiguration(holder, snapshot, document);
    }

    public static LoadedConfiguration writeAtomically(
                                                      ConfigHolder<DataEnergisticsConfiguration> holder,
                                                      Path target,
                                                      long revision) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(
                target.getParent(),
                "." + target.getFileName() + ".migration.",
                ".tmp");
        try {
            IConfigFormat format = holder.getFormat().createFormat();
            holder.values().forEach(value -> value.serializeValue(format));
            format.writeFile(temporary.toFile());
            forceFile(temporary);
            verifyUtf8WithoutBom(temporary);
            LoadedConfiguration validated = read(temporary, revision);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException(
                        "Atomic move is not supported for configuration migration temporary file " + temporary,
                        exception);
            }
            return new LoadedConfiguration(validated.holder(), validated.snapshot(), validated.document());
        } catch (IOException | RuntimeException exception) {
            throw new IOException(
                    "Failed to publish configuration YAML; migration evidence remains at " + temporary,
                    exception);
        }
    }

    public static LoadedConfiguration recoverTemporary(Path temporary, Path target, long revision) throws IOException {
        LoadedConfiguration validated = read(temporary, revision);
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            throw new IOException("Atomic move is not supported while recovering " + temporary, exception);
        }
        return validated;
    }

    public static List<Path> migrationTemporaries(Path target) throws IOException {
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

    static <T> T forceValue(
                            ConfigHolder<DataEnergisticsConfiguration> holder,
                            String path,
                            Class<T> type,
                            T value) {
        IConfigValue<T> configurable = holder.getConfigValue(path, type).orElseThrow(() -> new IllegalStateException(
                "Configuration schema has no " + type.getSimpleName() + " value at " + path));
        ConfigValue<T> concrete = configValue(configurable);
        concrete.forceSetValue(value);
        return concrete.getActiveValue();
    }

    @SuppressWarnings("unchecked")
    private static <T> ConfigValue<T> configValue(IConfigValue<T> value) {
        if (!(value instanceof ConfigValue<?> concrete)) {
            throw new IllegalStateException("Configuration value is not backed by ConfigValue: " + value.getPath());
        }
        return (ConfigValue<T>) concrete;
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
                    "generated YAML is not valid UTF-8",
                    exception.getMessage(),
                    exception);
        }
    }

    public record LoadedConfiguration(
                                      ConfigHolder<DataEnergisticsConfiguration> holder,
                                      ConfigurationSnapshot snapshot,
                                      StrictYamlReader.YamlDocument document) {}
}
