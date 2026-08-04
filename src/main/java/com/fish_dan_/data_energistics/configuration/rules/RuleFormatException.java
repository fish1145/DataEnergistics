package com.fish_dan_.data_energistics.configuration.rules;

import java.io.IOException;
import java.nio.file.Path;

/** Describes one precisely located validation failure in the Data Extractor rule file. */
public final class RuleFormatException extends IOException {

    public RuleFormatException(
                               Path source,
                               String jsonPath,
                               String violation,
                               String actualValue,
                               String repairAdvice) {
        super(message(source, jsonPath, violation, actualValue, repairAdvice));
    }

    public RuleFormatException(
                               Path source,
                               String jsonPath,
                               String violation,
                               String actualValue,
                               String repairAdvice,
                               Throwable cause) {
        super(message(source, jsonPath, violation, actualValue, repairAdvice), cause);
    }

    private static String message(
                                  Path source,
                                  String jsonPath,
                                  String violation,
                                  String actualValue,
                                  String repairAdvice) {
        return "Invalid Data Extractor rule file " + source.toAbsolutePath().normalize() + " at " + jsonPath + ": " + violation + "; actual=" + abbreviate(actualValue) + "; repair=" + repairAdvice;
    }

    private static String abbreviate(String value) {
        if (value == null) {
            return "<missing>";
        }
        String escaped = value.replace("\r", "\\r").replace("\n", "\\n");
        return escaped.length() <= 160 ? escaped : escaped.substring(0, 157) + "...";
    }
}
