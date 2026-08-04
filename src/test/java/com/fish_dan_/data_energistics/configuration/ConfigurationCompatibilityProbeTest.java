package com.fish_dan_.data_energistics.configuration;

import net.minecraft.network.chat.contents.TranslatableContents;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.ConfigHolder;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.UpdateRestrictions;
import dev.toma.configuration.config.exception.ConfigReadException;
import dev.toma.configuration.config.format.ConfigFormats;
import dev.toma.configuration.config.format.IConfigFormat;
import dev.toma.configuration.config.io.ConfigIO;
import dev.toma.configuration.config.value.ConfigValue;
import dev.toma.configuration.config.value.IConfigValue;
import dev.toma.configuration.config.value.IConfigValueReadable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfigurationCompatibilityProbeTest {

    private static final String CONFIG_ID = "data_energistics_configuration_probe";

    @TempDir
    Path temporaryDirectory;

    @Test
    void yamlRoundTripSupportsNestedValuesCommentsArraysEnumsAndFullLocalizationKeys() throws Exception {
        ConfigHolder<ProbeSchema> holder = newHolder();
        Path yamlFile = temporaryDirectory.resolve("probe.yaml");

        write(holder, yamlFile);

        assertEquals(
                List.of(
                        "nested:",
                        "  # Aliases accepted by the probe.",
                        "  # 探针接受的别名。",
                        "  aliases:",
                        "    - alpha",
                        "    - beta",
                        "",
                        "  # Allowed values:",
                        "  # - NET_NEW",
                        "  # - FINAL_TOTAL",
                        "  mode: NET_NEW",
                        "",
                        "plannerThreads: 4",
                        ""),
                Files.readAllLines(yamlFile, StandardCharsets.UTF_8));

        IConfigValue<String[]> aliases = holder.getConfigValue("nested.aliases", String[].class).orElseThrow();
        assertArrayEquals(
                new String[] { "Aliases accepted by the probe.", "探针接受的别名。" }, aliases.getFileComments());
        assertEquals(
                List.of("Aliases accepted by the probe.", "探针接受的别名。"),
                aliases.getDescription().stream().map(component -> component.getString()).toList());
        TranslatableContents title = assertInstanceOf(TranslatableContents.class, aliases.getTitle().getContents());
        assertEquals("config." + CONFIG_ID + ".option.nested.aliases", title.getKey());
        assertEquals("beta", holder.getValue("nested.aliases.1", String.class).orElseThrow());

        // spotless:off
        Files.writeString(
                yamlFile,
                """
                nested:
                  aliases:
                    - gamma
                    - delta

                  mode: FINAL_TOTAL

                plannerThreads: 6

                """,
                StandardCharsets.UTF_8);
        // spotless:on
        read(holder, yamlFile);

        ProbeSchema schema = holder.getConfigInstance();
        assertArrayEquals(new String[] { "gamma", "delta" }, schema.nested.aliases);
        assertEquals(ProbeMode.FINAL_TOTAL, schema.nested.mode);
        assertEquals(6, schema.plannerThreads);
    }

    @Test
    void gameRestartRestrictionKeepsTheChangePendingUntilLoading() {
        ConfigHolder<ProbeSchema> holder = newHolder();
        IConfigValue<Integer> plannerThreads = holder.getConfigValue("plannerThreads", Integer.class).orElseThrow();
        ConfigValue<?> storedPlannerThreads = assertInstanceOf(ConfigValue.class, plannerThreads);
        ConfigIO.ConfigEnvironment originalEnvironment = ConfigIO.getEnvironment();

        try {
            ConfigIO.setEnvironment(ConfigIO.ConfigEnvironment.PLAYING);
            assertFalse(plannerThreads.isEditable());
            plannerThreads.setValue(8);
            assertEquals(4, holder.getConfigInstance().plannerThreads);

            ConfigIO.setEnvironment(ConfigIO.ConfigEnvironment.MENU);
            assertTrue(plannerThreads.isEditable());
            plannerThreads.setValue(8);
            holder.save();
            assertTrue(plannerThreads.isChanged());
            assertEquals(4, storedPlannerThreads.getActiveValue());
            assertEquals(8, plannerThreads.get(IConfigValueReadable.Mode.SAVED));
            assertEquals(8, plannerThreads.get(IConfigValueReadable.Mode.PENDING));

            ConfigIO.setEnvironment(ConfigIO.ConfigEnvironment.LOADING);
            holder.save();
            assertFalse(plannerThreads.isChanged());
            assertEquals(8, storedPlannerThreads.getActiveValue());
            assertEquals(8, plannerThreads.get(IConfigValueReadable.Mode.SAVED));
        } finally {
            ConfigIO.setEnvironment(originalEnvironment);
        }
    }

    @Test
    void registrationAddsTheConfigToTheBuiltInWatcherAndReloadUsesTheHolderLock() throws Exception {
        Path configFile = Path.of("config", "configuration-probe", "configuration-watch-probe.yaml");
        Files.deleteIfExists(configFile);

        ConfigHolder<ProbeSchema> holder = Configuration.registerConfig(ProbeSchema.class, ConfigFormats.YAML);
        ProbeSchema schema = holder.getConfigInstance();
        Object holderLock = holder.getLock();
        assertSame(holderLock, holder.getLock());

        ConfigIO.FILE_WATCH_MANAGER.startService();
        try {
            synchronized (holderLock) {
                assertTrue(Thread.holdsLock(holderLock));
                // spotless:off
                Files.writeString(
                        configFile,
                        """
                        nested:
                          aliases:
                            - watched

                          mode: FINAL_TOTAL

                        plannerThreads: 4

                        """,
                        StandardCharsets.UTF_8);
                // spotless:on
                Thread.sleep(1_500L);
                assertArrayEquals(new String[] { "alpha", "beta" }, schema.nested.aliases);
            }

            await(Duration.ofSeconds(8), () -> schema.nested.aliases.length == 1);
            assertArrayEquals(new String[] { "watched" }, schema.nested.aliases);
            assertEquals(ProbeMode.FINAL_TOTAL, schema.nested.mode);
        } finally {
            Files.deleteIfExists(configFile);
        }
    }

    private static ConfigHolder<ProbeSchema> newHolder() {
        return new ConfigHolder<>(
                ProbeSchema.class,
                CONFIG_ID,
                "configuration-probe/configuration-watch-probe",
                CONFIG_ID,
                ConfigFormats.YAML);
    }

    private static void write(ConfigHolder<?> holder, Path file) throws IOException {
        IConfigFormat format = holder.getFormat().createFormat();
        holder.values().forEach(value -> value.serializeValue(format));
        format.writeFile(file.toFile());
    }

    private static void read(ConfigHolder<?> holder, Path file) throws IOException, ConfigReadException {
        IConfigFormat format = holder.getFormat().createFormat();
        format.readFile(file.toFile());
        holder.values().forEach(value -> value.deserializeValue(format));
        holder.save();
    }

    private static void await(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            Thread.sleep(50L);
        }
        assertTrue(condition.getAsBoolean(), "Timed out while waiting for the Configuration file watcher");
    }

    @Config(
            id = CONFIG_ID,
            filename = "configuration-probe/configuration-watch-probe")
    public static final class ProbeSchema {

        @Configurable
        public NestedSchema nested = new NestedSchema();

        @Configurable
        @Configurable.UpdateRestriction(UpdateRestrictions.GAME_RESTART)
        public int plannerThreads = 4;

        public static final class NestedSchema {

            @Configurable(key = Configurable.LocalizationKey.FULL)
            @Configurable.Comment({ "Aliases accepted by the probe.", "探针接受的别名。" })
            public String[] aliases = { "alpha", "beta" };

            @Configurable(key = Configurable.LocalizationKey.FULL)
            public ProbeMode mode = ProbeMode.NET_NEW;
        }
    }

    private enum ProbeMode {
        NET_NEW,
        FINAL_TOTAL
    }
}
