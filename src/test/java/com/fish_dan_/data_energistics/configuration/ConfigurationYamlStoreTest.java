package com.fish_dan_.data_energistics.configuration;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

import dev.toma.configuration.config.ConfigHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigurationYamlStoreTest {

    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    @SuppressWarnings("UnstableApiUsage")
    static void bootstrapMinecraftRegistries() {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void writesAndStrictlyReadsAllSixtyThreeValuesWithoutBom() throws IOException {
        Path target = ConfigurationYamlStore.target(temporaryDirectory);

        ConfigurationYamlStore.LoadedConfiguration loaded = ConfigurationYamlStore.writeAtomically(
                ConfigurationYamlStore.newHolder(),
                target,
                1L);

        byte[] bytes = Files.readAllBytes(target);
        assertFalse(bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB &&
                bytes[2] == (byte) 0xBF);
        assertEquals(63, loaded.document().values().size());
        assertEquals(1L, loaded.snapshot().revision());
        assertEquals(36, loaded.snapshot().dataExtractor().cropInputMappings().size());
        assertEquals(32768, loaded.snapshot().trinityCrafting().maxBindingVariants());
        assertTrue(ConfigurationYamlStore.migrationTemporaries(target).isEmpty());
    }

    @Test
    void acceptsYamlExplicitBindingLimitOfFiveHundredTwelve() throws IOException {
        Path target = ConfigurationYamlStore.target(temporaryDirectory);
        ConfigurationYamlStore.writeAtomically(ConfigurationYamlStore.newHolder(), target, 1L);
        String yaml = Files.readString(target, StandardCharsets.UTF_8)
                .replace("maxBindingVariants: 32768", "maxBindingVariants: 512");
        Files.writeString(target, yaml, StandardCharsets.UTF_8);

        ConfigurationYamlStore.LoadedConfiguration loaded = ConfigurationYamlStore.read(target, 2L);

        assertEquals(512, loaded.snapshot().trinityCrafting().maxBindingVariants());
    }

    @Test
    void rejectsMissingDuplicateUnknownAndFrameworkCorrectedValues() throws IOException {
        Path target = ConfigurationYamlStore.target(temporaryDirectory);
        ConfigurationYamlStore.writeAtomically(ConfigurationYamlStore.newHolder(), target, 1L);
        String valid = Files.readString(target, StandardCharsets.UTF_8);

        assertInvalid(
                target,
                valid.replace("  baseCost: 512\n", ""),
                "dataRipper.baseCost",
                "missing");
        assertInvalid(
                target,
                valid.replace("  baseCost: 512\n", "  baseCost: 512\n  baseCost: 1024\n"),
                "dataRipper.baseCost",
                "duplicate");
        assertInvalid(
                target,
                valid.replace("  baseCost: 512\n", "  baseCost: 512\n  mystery: 1\n"),
                "dataRipper.mystery",
                "unknown");
        assertInvalid(
                target,
                valid.replace("  baseCost: 512\n", "  baseCost: 0\n"),
                "dataRipper.baseCost",
                "corrected");
    }

    @Test
    void assemblesParsedRegexCsvMappingsBlockAndCrossFieldConstraints() throws IOException {
        Path source = temporaryDirectory.resolve("candidate.yaml");
        ConfigHolder<DataEnergisticsConfiguration> holder = ConfigurationYamlStore.newHolder();
        ConfigurationYamlStore.forceValue(
                holder,
                "dataRipper.blacklist",
                String[].class,
                new String[] { "minecraft:hopper", "appeng:.*" });
        ConfigurationYamlStore.forceValue(
                holder,
                "dataRipper.multipliers",
                String[].class,
                new String[] { "minecraft:hopper=2.5" });
        ConfigurationYamlStore.forceValue(
                holder,
                "dataExtractor.mobDataBlacklist",
                String.class,
                "minecraft:zombie,minecraft:skeleton");
        ConfigurationYamlStore.forceValue(
                holder,
                "flatteningTnt.tntConfigurable.fillBlock",
                String.class,
                "minecraft:stone");
        ConfigurationYamlStore.writeAtomically(holder, source, 3L);

        ConfigurationSnapshot snapshot = ConfigurationYamlStore.read(source, 3L).snapshot();

        assertEquals(2, snapshot.dataRipper().blacklist().size());
        assertEquals(2.5D, snapshot.dataRipper().multipliers().getFirst().value());
        assertEquals(2, snapshot.dataExtractor().mobDataBlacklist().size());
        assertEquals(
                "minecraft:stone",
                BuiltInRegistries.BLOCK.getKey(snapshot.flatteningTnt().fillBlockState().getBlock()).toString());

        ConfigurationYamlStore.forceValue(holder, "trinityDispatch.safeGridAttempts", Integer.class, 300);
        InvalidConfigurationException exception = assertThrows(
                InvalidConfigurationException.class,
                () -> SnapshotAssembler.assemble(holder.getConfigInstance(), source, 4L));
        assertTrue(exception.getMessage().contains("trinityDispatch.safeGridAttempts"));
    }

    private static void assertInvalid(Path target, String content, String path, String reason) throws IOException {
        Files.writeString(target, content, StandardCharsets.UTF_8);
        InvalidConfigurationException exception = assertThrows(
                InvalidConfigurationException.class,
                () -> ConfigurationYamlStore.read(target, 2L));
        assertTrue(exception.getMessage().contains(path), exception.getMessage());
        assertTrue(exception.getMessage().toLowerCase().contains(reason), exception.getMessage());
    }
}
