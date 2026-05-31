package com.fish_dan_.data_energistics.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class DataRipperConfigParsingUtils {

    private DataRipperConfigParsingUtils() {}

    public record MultiplierEntry(Pattern pattern, double value) {}

    public static List<Pattern> precompilePatterns(List<String> textPatterns) {
        if (textPatterns == null || textPatterns.isEmpty()) {
            return List.of();
        }
        List<Pattern> patterns = new ArrayList<>(textPatterns.size());
        for (String entry : textPatterns) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            try {
                patterns.add(Pattern.compile(entry));
            } catch (PatternSyntaxException ignored) {}
        }
        return List.copyOf(patterns);
    }

    public static List<MultiplierEntry> precompileMultipliers(List<String> textMultipliers) {
        if (textMultipliers == null || textMultipliers.isEmpty()) {
            return List.of();
        }
        List<MultiplierEntry> entries = new ArrayList<>(textMultipliers.size());
        for (String entry : textMultipliers) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator >= entry.length() - 1) {
                continue;
            }
            String patternText = entry.substring(0, separator).trim();
            String valueText = entry.substring(separator + 1).trim();
            try {
                double value = Double.parseDouble(valueText);
                entries.add(new MultiplierEntry(Pattern.compile(patternText), value));
            } catch (NumberFormatException | PatternSyntaxException ignored) {}
        }
        return List.copyOf(entries);
    }

    public static boolean isBlockBlacklisted(String blockId, List<Pattern> blacklist) {
        if (blockId == null || blacklist == null || blacklist.isEmpty()) {
            return false;
        }
        for (Pattern pattern : blacklist) {
            if (pattern.matcher(blockId).matches()) {
                return true;
            }
        }
        return false;
    }

    public static double getMultiplierForBlock(String blockId, List<MultiplierEntry> multipliers) {
        if (blockId == null || multipliers == null || multipliers.isEmpty()) {
            return 1.0D;
        }
        double maxMultiplier = 1.0D;
        for (MultiplierEntry entry : multipliers) {
            if (entry.pattern().matcher(blockId).matches()) {
                maxMultiplier = Math.max(maxMultiplier, entry.value());
            }
        }
        return maxMultiplier;
    }
}
