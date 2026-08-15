package com.fish_dan_.data_energistics.client.screen.trinity;

import com.fish_dan_.data_energistics.client.util.PinyinUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds stable, locale-independent search text from localized pattern names for the Trinity access terminal.
 */
public final class TrinityPatternSearchMatcher {

    /**
     * Compares one query directly against cached candidate groups without joining and splitting their names on every
     * search pass.
     *
     * @param inputs     localized input names in their display order
     * @param outputs    localized output names in their display order
     * @param extraTerms localized machine-specific names included in every scope
     * @param mode       scope that decides which ordinary names are included
     * @param query      user-entered search query
     * @return {@code true} for a blank query or when one candidate contains every query token in order
     * @throws NullPointerException if a collection, the mode, the query, or any candidate name is {@code null}
     */
    public boolean matches(List<String> inputs,
                           List<String> outputs,
                           List<String> extraTerms,
                           TrinityPatternSearchMode mode,
                           String query) {
        String normalizedQuery = normalizeText(query);
        List<String> queryTokens = tokenize(normalizedQuery);
        if (queryTokens.isEmpty()) {
            return true;
        }

        return switch (mode) {
            case INPUT -> matchesCandidates(inputs, queryTokens, normalizedQuery) ||
                    matchesCandidates(extraTerms, queryTokens, normalizedQuery);
            case OUTPUT -> matchesCandidates(outputs, queryTokens, normalizedQuery) ||
                    matchesCandidates(extraTerms, queryTokens, normalizedQuery);
            case INPUT_OUTPUT -> matchesCandidates(inputs, queryTokens, normalizedQuery) ||
                    matchesCandidates(outputs, queryTokens, normalizedQuery) ||
                    matchesCandidates(extraTerms, queryTokens, normalizedQuery);
        };
    }

    private static boolean matchesCandidates(
                                             List<String> candidates,
                                             List<String> queryTokens,
                                             String normalizedQuery) {
        for (String candidate : candidates) {
            String normalizedCandidate = normalizeText(candidate);
            if (containsOrderedTokens(normalizedCandidate, queryTokens) ||
                    PinyinUtil.matchesNormalizedJech(candidate, normalizedQuery)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Applies stable case normalization before token comparison.
     *
     * @param text non-null text to normalize
     * @return lower-case text without surrounding ASCII whitespace
     */
    private static String normalizeText(String text) {
        return text.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * Splits normalized text on spaces and discards empty or whitespace-only segments.
     *
     * @param text normalized text to split
     * @return tokens in their original order
     */
    private static List<String> tokenize(String text) {
        String[] splitTokens = text.split(" ");
        ArrayList<String> tokens = new ArrayList<>(splitTokens.length);
        for (String token : splitTokens) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * Replicates EAE token comparison by consuming matching candidate tokens in forward order.
     *
     * @param candidate   one normalized candidate name
     * @param queryTokens normalized query tokens
     * @return whether every query token is contained by an ordered candidate-token subsequence
     */
    private static boolean containsOrderedTokens(String candidate, List<String> queryTokens) {
        int candidateOffset = 0;
        for (String queryToken : queryTokens) {
            boolean matched = false;
            while (candidateOffset < candidate.length()) {
                while (candidateOffset < candidate.length() && candidate.charAt(candidateOffset) == ' ') {
                    candidateOffset++;
                }
                if (candidateOffset == candidate.length()) {
                    break;
                }
                int tokenEnd = candidate.indexOf(' ', candidateOffset);
                if (tokenEnd < 0) {
                    tokenEnd = candidate.length();
                }
                int matchOffset = candidate.indexOf(queryToken, candidateOffset);
                candidateOffset = tokenEnd + 1;
                if (matchOffset >= 0 && matchOffset < tokenEnd) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }
}
