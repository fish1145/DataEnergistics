package com.fish_dan_.data_energistics.configuration.validation;

import java.io.IOException;
import java.nio.file.Path;

/** Reports one precisely located failure at the external configuration boundary. */
public final class InvalidConfigurationException extends IOException {

    public InvalidConfigurationException(Path source, String path, String violation, String actualValue) {
        super("Invalid configuration " + source.toAbsolutePath().normalize() + " at " + path + ": " + violation +
                "; actual=" + abbreviate(actualValue));
    }

    public InvalidConfigurationException(
                                         Path source,
                                         String path,
                                         String violation,
                                         String actualValue,
                                         Throwable cause) {
        super("Invalid configuration " + source.toAbsolutePath().normalize() + " at " + path + ": " + violation +
                "; actual=" + abbreviate(actualValue), cause);
    }

    private static String abbreviate(String value) {
        String escaped = value.replace("\r", "\\r").replace("\n", "\\n");
        return escaped.length() <= 160 ? escaped : escaped.substring(0, 157) + "...";
    }
}
