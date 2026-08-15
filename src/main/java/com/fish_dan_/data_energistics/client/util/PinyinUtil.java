package com.fish_dan_.data_energistics.client.util;

import com.fish_dan_.data_energistics.integration.ModFlags;

import me.towdium.jecharacters.utils.Match;

public final class PinyinUtil {

    private PinyinUtil() {}

    public static boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT;
    }

    public static String normalizeSearch(String text) {
        if (text.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch) || isCjk(ch)) {
                builder.append(Character.toLowerCase(ch));
            }
        }
        return builder.toString();
    }

    public static boolean matchesSearch(String text, String filter) {
        if (text.isEmpty()) {
            return false;
        }
        if (filter.isEmpty()) {
            return true;
        }

        String normalized = normalizeSearch(text);
        String normalizedFilter = normalizeSearch(filter);
        if (normalizedFilter.isEmpty()) {
            return true;
        }

        if (normalized.contains(normalizedFilter) || isSubsequenceMatch(normalizedFilter, normalized)) {
            return true;
        }

        return matchesNormalizedJech(text, normalizedFilter);
    }

    /**
     * Matches one normalized query through JECh. Callers that compare the same query with multiple candidates can
     * normalize it once and reuse this method.
     *
     * @param text             candidate text shown by the UI
     * @param normalizedFilter query normalized by {@link #normalizeSearch(String)}
     * @return whether JECh matches the candidate, or {@code false} when JECh is not loaded
     */
    public static boolean matchesNormalizedJech(String text, String normalizedFilter) {
        if (!ModFlags.isJechLoaded() || text.isEmpty() || normalizedFilter.isEmpty()) {
            return false;
        }
        return JechMatcher.contains(text, normalizedFilter);
    }

    private static boolean isSubsequenceMatch(String filter, String variant) {
        if (filter.isEmpty()) {
            return true;
        }
        if (variant.isEmpty()) {
            return false;
        }
        int filterIndex = 0;
        for (int i = 0; i < variant.length() && filterIndex < filter.length(); i++) {
            if (variant.charAt(i) == filter.charAt(filterIndex)) {
                filterIndex++;
            }
        }
        return filterIndex == filter.length();
    }

    private static final class JechMatcher {

        static boolean contains(String text, String filter) {
            return Match.contains(text, filter, true);
        }
    }
}
