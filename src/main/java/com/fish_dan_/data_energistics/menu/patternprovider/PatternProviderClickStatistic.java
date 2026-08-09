package com.fish_dan_.data_energistics.menu.patternprovider;

import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;

import java.util.regex.Pattern;

/**
 * Stores one bounded provider-selection history entry for an exact ranking context.
 */
public record PatternProviderClickStatistic(
                                            PatternEncodingRankingContext context,
                                            String providerDigest,
                                            long count,
                                            long lastUsedEpochMillis) {

    public static final int DIGEST_LENGTH = 71;
    private static final Pattern DIGEST_PATTERN = Pattern.compile("sha256:[0-9a-f]{64}");

    /**
     * Rejects malformed or unbounded values before they reach persistence or menu ordering.
     */
    public PatternProviderClickStatistic {
        if (context == null) {
            throw new IllegalArgumentException("Pattern provider statistic context must not be null");
        }
        if (providerDigest == null || !DIGEST_PATTERN.matcher(providerDigest).matches()) {
            throw new IllegalArgumentException("Invalid pattern provider digest: " + providerDigest);
        }
        if (count < 0L) {
            throw new IllegalArgumentException("Pattern provider statistic count must not be negative: " + count);
        }
        if (lastUsedEpochMillis < 0L) {
            throw new IllegalArgumentException("Pattern provider statistic time must not be negative: " + lastUsedEpochMillis);
        }
    }

    /**
     * Returns the stable key used for deterministic eviction and duplicate detection.
     */
    public String stableKey() {
        return this.context.recipeTypeId() + "\0" + this.providerDigest;
    }
}
