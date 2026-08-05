package com.fish_dan_.data_energistics.configuration.rules;

import java.io.IOException;
import java.nio.file.Path;

/** Describes one precisely located validation failure in the active rule YAML. */
public final class RuleFormatException extends IOException {

    public RuleFormatException(
                               Path source,
                               String location,
                               String violation,
                               String actualValue,
                               String repairAdvice) {
        super(message(source, location, violation, actualValue, repairAdvice));
    }

    public RuleFormatException(
                               Path source,
                               String location,
                               String violation,
                               String actualValue,
                               String repairAdvice,
                               Throwable cause) {
        super(message(source, location, violation, actualValue, repairAdvice), cause);
    }

    private static String message(
                                  Path source,
                                  String location,
                                  String violation,
                                  String actualValue,
                                  String repairAdvice) {
        return "Invalid Data Extractor rule file " + source.toAbsolutePath().normalize() + " at " + location + ": " + violation + "; actual=" + abbreviate(actualValue) + "; repair=" + repairAdvice;
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "<missing>";
        }
        String escaped = value.replace("\r", "\\r").replace("\n", "\\n");
        return escaped.length() <= 160 ? escaped : escaped.substring(0, 157) + "...";
    }
}
