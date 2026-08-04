package com.fish_dan_.data_energistics.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class DataRipperConfigParsingUtils {

    private DataRipperConfigParsingUtils() {}

    public record MultiplierEntry(Pattern pattern, double value) {}

    public static List<Pattern> precompilePatterns(List<String> textPatterns) {
        List<Pattern> patterns = new ArrayList<>(textPatterns.size());
        for (String entry : textPatterns) {
            if (entry.isBlank()) {
                throw new IllegalArgumentException("Data Ripper blacklist regex must not be blank");
            }
            patterns.add(Pattern.compile(entry));
        }
        return List.copyOf(patterns);
    }

    public static List<MultiplierEntry> precompileMultipliers(List<String> textMultipliers) {
        List<MultiplierEntry> entries = new ArrayList<>(textMultipliers.size());
        for (String entry : textMultipliers) {
            int separator = entry.lastIndexOf('=');
            if (separator <= 0 || separator >= entry.length() - 1) {
                throw new IllegalArgumentException("Data Ripper multiplier must use pattern=value: " + entry);
            }
            String patternText = entry.substring(0, separator).trim();
            String valueText = entry.substring(separator + 1).trim();
            if (patternText.isEmpty()) {
                throw new IllegalArgumentException("Data Ripper multiplier regex must not be blank: " + entry);
            }
            double value = Double.parseDouble(valueText);
            if (!Double.isFinite(value) || value <= 0.0D) {
                throw new IllegalArgumentException("Data Ripper multiplier must be finite and positive: " + entry);
            }
            entries.add(new MultiplierEntry(Pattern.compile(patternText), value));
        }
        return List.copyOf(entries);
    }

    public static boolean isBlockBlacklisted(String blockId, List<Pattern> blacklist) {
        for (Pattern pattern : blacklist) {
            if (pattern.matcher(blockId).matches()) {
                return true;
            }
        }
        return false;
    }

    public static double getMultiplierForBlock(String blockId, List<MultiplierEntry> multipliers) {
        double maxMultiplier = 1.0D;
        for (MultiplierEntry entry : multipliers) {
            if (entry.pattern().matcher(blockId).matches()) {
                maxMultiplier = Math.max(maxMultiplier, entry.value());
            }
        }
        return maxMultiplier;
    }
}
