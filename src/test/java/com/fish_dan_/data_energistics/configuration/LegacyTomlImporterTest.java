package com.fish_dan_.data_energistics.configuration;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LegacyTomlImporterTest {

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
    void importsTheFixedSixtyFourFieldInventoryWithoutChangingLegacyFiles() throws Exception {
        Map<Path, byte[]> sources = writeCompleteLegacyFiles();

        LegacyTomlImporter.PreparedConfiguration prepared = LegacyTomlImporter.prepare(temporaryDirectory);

        assertEquals(64, LegacyTomlImporter.recognizedLegacyFieldCount());
        assertTrue(prepared.importedLegacyFiles());
        assertFalse(prepared.recoveredTemporary());
        assertEquals(63, prepared.loaded().document().values().size());
        ConfigurationSnapshot snapshot = prepared.loaded().snapshot();
        assertEquals(1024, snapshot.dataRipper().baseCost());
        assertEquals(List.of("minecraft:hopper"), snapshot.dataRipper().blacklistText());
        assertEquals(2.5D, snapshot.dataRipper().multipliers().getFirst().value());
        assertEquals(2, snapshot.dataDistributionTower().range());
        assertEquals(100, snapshot.dataSanctumInterface().itemLimit());
        assertEquals(101, snapshot.dataSanctumInterface().fluidBuckets());
        assertEquals(102, snapshot.dataSanctumInterface().returnItemLimit());
        assertEquals(103, snapshot.dataSanctumInterface().returnFluidBuckets());
        assertEquals(6, snapshot.dataExtractor().baseDamage());
        assertEquals(6, snapshot.dataExtractor().workIntervalSeconds());
        assertEquals(101, snapshot.dataExtractor().baseDataFlowPerCycle());
        assertEquals(21, snapshot.dataExtractor().dataFlowPerSwordDamage());
        assertEquals(21, snapshot.dataExtractor().baseTargetLimit());
        assertEquals(6, snapshot.dataExtractor().targetLimitPerCapacityCard());
        assertEquals(0.5D, snapshot.dataExtractor().extraTargetDataFlowMultiplier());
        assertEquals(2048.0F, snapshot.dataExtractor().mobRequiredDamage());
        assertEquals(8192.0F, snapshot.dataExtractor().oreRequiredAmount());
        assertEquals(16384.0F, snapshot.dataExtractor().cropRequiredAmount());
        assertEquals(1, snapshot.dataExtractor().mobDataBlacklist().size());
        assertEquals(1, snapshot.dataExtractor().oreDataBlacklist().size());
        assertEquals(1, snapshot.dataExtractor().cropDataBlacklist().size());
        assertEquals(1, snapshot.dataExtractor().cropDataWhitelist().size());
        assertEquals(1, snapshot.dataExtractor().cropInputMappings().size());
        assertEquals(2, snapshot.flatteningTnt().clearChunkRadius());
        assertEquals(3, snapshot.flatteningTnt().clearStartYOffset());
        assertEquals(30, snapshot.flatteningTnt().clearHeight());
        assertEquals(2, snapshot.flatteningTnt().fillChunkRadius());
        assertEquals(-2, snapshot.flatteningTnt().fillYOffset());
        assertEquals(1, snapshot.flatteningTnt().explosionCenterOffset().getX());
        assertEquals(2, snapshot.flatteningTnt().explosionCenterOffset().getY());
        assertEquals(3, snapshot.flatteningTnt().explosionCenterOffset().getZ());
        assertTrue(snapshot.flatteningTnt().preserveFluids());
        assertTrue(snapshot.flatteningTnt().replaceUnbreakableBlocks());
        assertEquals(2, snapshot.dataNuke().workIntervalTicks());
        assertEquals(1024, snapshot.dataNuke().maxRadius());
        assertEquals(5.0D, snapshot.dataNuke().centerEntityConsumeRadius());
        assertEquals(4000.0D, snapshot.solarPanel().dayGenerationAEPerTick());
        assertEquals(2000.0D, snapshot.solarPanel().nightGenerationAEPerTick());
        assertEquals(1.0D, snapshot.solarPanel().speedCardBonusRatio());
        assertEquals(90000.0D, snapshot.solarPanel().energyCardCapacityBonusAE());
        assertEquals(65, snapshot.trinityCrafting().maxSccKeys());
        assertEquals(32768, snapshot.trinityCrafting().maxBindingVariants());
        assertEquals(600000, snapshot.trinityCrafting().maxScheduleStates());
        assertEquals(5, snapshot.trinityCrafting().graphRebuildBudgetMs());
        assertEquals(2, snapshot.trinityCrafting().plannerThreads());
        assertEquals(129, snapshot.trinityCrafting().plannerQueueCapacity());
        assertEquals(201, snapshot.trinityCrafting().dynamicRetryMaxTicks());
        assertEquals("FINAL_TOTAL", snapshot.trinityCrafting().defaultQuantityMode().name());
        assertEquals(300, snapshot.trinityDispatch().hardGridAttempts());
        assertEquals(20, snapshot.trinityDispatch().hardProviderAttempts());
        assertEquals(40, snapshot.trinityDispatch().hardCommitBudgetMs());
        assertEquals(20, snapshot.trinityDispatch().safeGridAttempts());
        assertEquals(3, snapshot.trinityDispatch().safeProviderAttempts());
        assertEquals(3, snapshot.trinityDispatch().safeCommitBudgetMs());
        assertEquals(2, snapshot.trinityDispatch().safeActorPermits());
        assertEquals(8, snapshot.trinityDispatch().safeRetryBackoffTicks());
        assertEquals(201, snapshot.trinityDispatch().warmupTicks());
        assertEquals(21, snapshot.trinityDispatch().metricsWindowTicks());
        assertEquals(0.5D, snapshot.trinityDispatch().ewmaAlpha());
        assertEquals(4, snapshot.trinityDispatch().transitionWindows());
        assertEquals(61, snapshot.trinityDispatch().cooldownTicks());
        assertEquals(201, snapshot.trinityDispatch().safeHoldTicks());

        for (Map.Entry<Path, byte[]> source : sources.entrySet()) {
            assertArrayEquals(source.getValue(), sha256(Files.readAllBytes(source.getKey())));
        }
    }

    @Test
    void validYamlAlwaysWinsAndPreventsASecondTomlImport() throws IOException {
        LegacyTomlImporter.PreparedConfiguration first = LegacyTomlImporter.prepare(temporaryDirectory);
        Path legacy = temporaryDirectory.resolve("data_energistics-common.toml");
        Files.writeString(legacy, "dataRipperBaseCost = 999\n", StandardCharsets.UTF_8);

        LegacyTomlImporter.PreparedConfiguration second = LegacyTomlImporter.prepare(temporaryDirectory);

        assertFalse(first.importedLegacyFiles());
        assertFalse(second.importedLegacyFiles());
        assertEquals(512, second.loaded().snapshot().dataRipper().baseCost());
    }

    @Test
    void rejectsUnknownWrongTypeOutOfRangeAndDispatchCrossFieldValues() throws IOException {
        assertRejected("data_energistics-common.toml", "mystery = 1\n", "mystery");
        assertRejected("data_energistics-common.toml", "dataRipperBaseCost = \"512\"\n", "dataRipperBaseCost");
        assertRejected("data_energistics-common.toml", "dataRipperBaseCost = 0\n", "dataRipper.baseCost");
        assertRejected(
                "data_energistics-trinity_dispatch.toml",
                "hardGridAttempts = 4\nsafeGridAttempts = 5\n",
                "trinityDispatch.safeGridAttempts");
    }

    @Test
    void rejectsTheNewDispatchKeyAsAnUnknownLegacyInput() throws IOException {
        assertRejected(
                "data_energistics-trinity_dispatch.toml",
                "safeRetryBackoffTicks = 9\n",
                "safeRetryBackoffTicks");
    }

    @Test
    void recoversOneCompleteMigrationTemporaryButRejectsAmbiguity() throws IOException {
        Path target = ConfigurationYamlStore.target(temporaryDirectory);
        Files.createDirectories(target.getParent());
        Path firstTemporary = target.getParent().resolve(".data_energistics.yaml.migration.first.tmp");
        ConfigurationYamlStore.writeAtomically(ConfigurationYamlStore.newHolder(), firstTemporary, 0L);

        LegacyTomlImporter.PreparedConfiguration recovered = LegacyTomlImporter.prepare(temporaryDirectory);

        assertTrue(recovered.recoveredTemporary());
        assertTrue(Files.exists(target));
        assertFalse(Files.exists(firstTemporary));

        Files.delete(target);
        Path secondTemporary = target.getParent().resolve(".data_energistics.yaml.migration.second.tmp");
        Path thirdTemporary = target.getParent().resolve(".data_energistics.yaml.migration.third.tmp");
        ConfigurationYamlStore.writeAtomically(ConfigurationYamlStore.newHolder(), secondTemporary, 0L);
        ConfigurationYamlStore.writeAtomically(ConfigurationYamlStore.newHolder(), thirdTemporary, 0L);

        InvalidConfigurationException exception = assertThrows(
                InvalidConfigurationException.class,
                () -> LegacyTomlImporter.prepare(temporaryDirectory));
        assertTrue(exception.getMessage().contains("multiple migration temporary"));
    }

    private void assertRejected(String filename, String content, String path) throws IOException {
        Path root = Files.createDirectory(temporaryDirectory.resolve("case-" + temporaryDirectory.toFile().list().length));
        Files.writeString(root.resolve(filename), content, StandardCharsets.UTF_8);

        InvalidConfigurationException exception = assertThrows(
                InvalidConfigurationException.class,
                () -> LegacyTomlImporter.prepare(root));

        assertTrue(exception.getMessage().contains(path), exception.getMessage());
        assertFalse(Files.exists(ConfigurationYamlStore.target(root)));
    }

    private Map<Path, byte[]> writeCompleteLegacyFiles() throws IOException, NoSuchAlgorithmException {
        Map<String, String> contents = new LinkedHashMap<>();
        contents.put("data_energistics-common.toml", """
                dataRipperBaseCost = 1024
                dataRipperBlacklist = ["minecraft:hopper"]
                dataRipperMultipliers = ["minecraft:hopper=2.5"]
                dataDistributionTowerRange = 2
                dataSanctumInterfaceItemLimit = 100
                dataSanctumInterfaceFluidBuckets = 101
                dataSanctumInterfaceReturnItemLimit = 102
                dataSanctumInterfaceReturnFluidBuckets = 103
                """);
        contents.put("data_energistics-data_extractor.toml", """
                baseDamage = 6
                workIntervalSeconds = 6
                baseDataFlowPerCycle = 101
                dataFlowPerSwordDamage = 21
                baseTargetLimit = 21
                targetLimitPerCapacityCard = 6
                extraTargetDataFlowMultiplier = 0.5
                mobRequiredDamage = 2048.0
                mobDataBlacklist = "minecraft:zombie"
                oreRequiredAmount = 8192.0
                oreDataBlacklist = "minecraft:raw_iron"
                cropRequiredAmount = 16384.0
                cropDataBlacklist = "minecraft:wheat"
                cropDataWhitelist = "minecraft:carrot"
                cropInputMappings = "minecraft:wheat_seeds=minecraft:wheat@1.0"
                """);
        contents.put("data_energistics-tnt.toml", """
                [flatteningTnt.tntConfigurable]
                clearChunkRadius = 2
                clearStartYOffset = 3
                clearHeight = 30
                fillChunkRadius = 2
                fillYOffset = -2
                fillBlock = "minecraft:stone"
                centerOffsetX = 1
                centerOffsetY = 2
                centerOffsetZ = 3
                preserveFluids = true
                replaceUnbreakableBlocks = true
                displayName = "legacy name"

                [flatteningTnt.dataNuke]
                workIntervalTicks = 2
                maxRadius = 1024
                centerEntityConsumeRadius = 5.0
                """);
        contents.put("data_energistics-solar_panel.toml", """
                dayGenerationAEPerTick = 4000.0
                nightGenerationAEPerTick = 2000.0
                speedCardBonusRatio = 1.0
                energyCardCapacityBonusAE = 90000.0
                """);
        contents.put("data_energistics-trinity_crafting.toml", """
                maxSccKeys = 65
                maxBindingVariants = 512
                maxScheduleStates = 600000
                graphRebuildBudgetMs = 5
                plannerThreads = 2
                plannerQueueCapacity = 129
                dynamicRetryMaxTicks = 201
                defaultQuantityMode = "FINAL_TOTAL"
                mipTimeoutMs = 1000
                """);
        contents.put("data_energistics-trinity_dispatch.toml", """
                hardGridAttempts = 300
                hardProviderAttempts = 20
                hardCommitBudgetMs = 40
                safeGridAttempts = 20
                safeProviderAttempts = 3
                safeCommitBudgetMs = 3
                safeActorPermits = 2
                warmupTicks = 201
                metricsWindowTicks = 21
                ewmaAlpha = 0.5
                transitionWindows = 4
                cooldownTicks = 61
                safeHoldTicks = 201
                """);

        Map<Path, byte[]> hashes = new LinkedHashMap<>();
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Map.Entry<String, String> entry : contents.entrySet()) {
            Path path = temporaryDirectory.resolve(entry.getKey());
            Files.writeString(path, entry.getValue(), StandardCharsets.UTF_8);
            hashes.put(path, digest.digest(Files.readAllBytes(path)));
        }
        return hashes;
    }

    private static byte[] sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }
}
