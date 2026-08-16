package com.fish_dan_.data_energistics.mixin.configuration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

/** Startup-only settings that must be known before Data Energistics Mixins transform their targets. */
public final class DataEnergisticsEarlyConfig {

    private static final Logger LOGGER = LogManager.getLogger("DataEnergisticsEarlyConfig");
    private static final Path CONFIG_DIRECTORY = Path.of("config", "data_energistics");
    private static final Path CONFIG_PATH = CONFIG_DIRECTORY.resolve("data_energistics-early.properties");
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final DataEnergisticsEarlyConfig INSTANCE = load();

    private final Set<Option> enabledOptions;

    private DataEnergisticsEarlyConfig(Set<Option> enabledOptions) {
        this.enabledOptions = Set.copyOf(enabledOptions);
    }

    public static DataEnergisticsEarlyConfig get() {
        return INSTANCE;
    }

    /** Forces this configuration to load during Mixin plugin construction. */
    public static void initialize() {
        LOGGER.debug("Early Mixin settings initialized from {}", CONFIG_PATH);
    }

    public boolean isEnabled(Option option) {
        return this.enabledOptions.contains(option);
    }

    private static DataEnergisticsEarlyConfig load() {
        try {
            Files.createDirectories(CONFIG_DIRECTORY);
            if (Files.notExists(CONFIG_PATH)) {
                Files.writeString(
                        CONFIG_PATH,
                        renderConfig(List.of(Option.values()), true),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW);
            }

            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            appendMissingOptions(properties);

            EnumSet<Option> enabledOptions = EnumSet.noneOf(Option.class);
            for (Option option : Option.values()) {
                if (readBoolean(properties, option)) {
                    enabledOptions.add(option);
                }
            }
            LOGGER.info("Loaded {} early Mixin option(s): {}", Option.values().length, enabledOptions);
            return new DataEnergisticsEarlyConfig(enabledOptions);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load early Mixin settings from " + CONFIG_PATH, exception);
        }
    }

    private static void appendMissingOptions(Properties properties) throws IOException {
        List<Option> missingOptions = new ArrayList<>();
        for (Option option : Option.values()) {
            if (!properties.containsKey(option.key())) {
                missingOptions.add(option);
            }
        }
        if (missingOptions.isEmpty()) {
            return;
        }

        Files.writeString(
                CONFIG_PATH,
                LINE_SEPARATOR + renderConfig(missingOptions, false),
                StandardCharsets.UTF_8,
                StandardOpenOption.APPEND);
    }

    private static String renderConfig(List<Option> options, boolean includeHeader) {
        StringBuilder config = new StringBuilder();
        if (includeHeader) {
            config.append("# Data Energistics early Mixin settings. Changes require a full game restart.")
                    .append(LINE_SEPARATOR);
        }
        for (Option option : options) {
            config.append("# ")
                    .append(option.description())
                    .append(LINE_SEPARATOR)
                    .append(option.key())
                    .append('=')
                    .append(option.defaultEnabled())
                    .append(LINE_SEPARATOR);
        }
        return config.toString();
    }

    private static boolean readBoolean(Properties properties, Option option) {
        String configuredValue = properties.getProperty(option.key());
        if (configuredValue == null) {
            return option.defaultEnabled();
        }

        return switch (configuredValue.trim().toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> {
                LOGGER.warn(
                        "Ignoring invalid early Mixin setting {}={}; expected true or false",
                        option.key(),
                        configuredValue);
                yield option.defaultEnabled();
            }
        };
    }

    /** Boolean switches available before Mixin application. */
    public enum Option {

        PATTERN_ENCODING_NETWORK_BACKED_BLANK_PATTERN_SLOT(
                "pattern_encoding.network_backed_blank_pattern_slot",
                true,
                "Use the Data Energistics network-backed blank pattern slot; false keeps AE2's local slot.");

        private final String key;
        private final boolean defaultEnabled;
        private final String description;

        Option(String key, boolean defaultEnabled, String description) {
            this.key = key;
            this.defaultEnabled = defaultEnabled;
            this.description = description;
        }

        public String key() {
            return this.key;
        }

        public boolean defaultEnabled() {
            return this.defaultEnabled;
        }

        private String description() {
            return this.description;
        }
    }
}
