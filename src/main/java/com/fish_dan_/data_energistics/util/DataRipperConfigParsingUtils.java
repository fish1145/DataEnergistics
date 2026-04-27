package com.fish_dan_.data_energistics.util;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class DataRipperConfigParsingUtils {
    private DataRipperConfigParsingUtils() {
    }

    public static boolean isBlockBlacklisted(String blockId, List<String> blacklist) {
        if (blockId == null || blacklist == null || blacklist.isEmpty()) {
            return false;
        }

        for (String entry : blacklist) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            try {
                if (Pattern.compile(entry).matcher(blockId).matches()) {
                    return true;
                }
            } catch (PatternSyntaxException ignored) {
            }
        }

        return false;
    }

    public static double getMultiplierForBlock(String blockId, List<String> multipliers) {
        if (blockId == null || multipliers == null || multipliers.isEmpty()) {
            return 1.0D;
        }

        double maxMultiplier = 1.0D;
        for (String entry : multipliers) {
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
                if (Pattern.compile(patternText).matcher(blockId).matches()) {
                    maxMultiplier = Math.max(maxMultiplier, Double.parseDouble(valueText));
                }
            } catch (NumberFormatException | PatternSyntaxException ignored) {
            }
        }

        return maxMultiplier;
    }
}
