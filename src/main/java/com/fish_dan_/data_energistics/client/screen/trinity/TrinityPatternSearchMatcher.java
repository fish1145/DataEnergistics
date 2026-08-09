package com.fish_dan_.data_energistics.client.screen.trinity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds stable, locale-independent search text from localized pattern names for the Trinity access terminal.
 */
public final class TrinityPatternSearchMatcher {

    /**
     * Validates both candidate groups before selecting the requested search scope, ensuring malformed cached data fails
     * at the matcher boundary.
     *
     * @param inputs  localized input names in their display order
     * @param outputs localized output names in their display order
     * @param mode    scope that decides which names are included
     * @return lower-case candidate names separated by newlines
     * @throws NullPointerException if a collection, the mode, or any candidate name is {@code null}
     */
    public String createSearchText(List<String> inputs, List<String> outputs, TrinityPatternSearchMode mode) {
        List<String> normalizedInputs = normalize(inputs);
        List<String> normalizedOutputs = normalize(outputs);

        return switch (mode) {
            case INPUT -> String.join("\n", normalizedInputs);
            case OUTPUT -> String.join("\n", normalizedOutputs);
            case INPUT_OUTPUT -> joinInputsAndOutputs(normalizedInputs, normalizedOutputs);
        };
    }

    /**
     * Compares query tokens against each candidate name independently using EAE's ordered partial-token semantics.
     *
     * @param searchText newline-delimited candidate names
     * @param query      user-entered search query
     * @return {@code true} for a blank query or when one candidate contains every query token in order
     * @throws NullPointerException if the search text or query is {@code null}
     */
    public boolean matchesSearchText(String searchText, String query) {
        List<String> queryTokens = tokenize(normalizeText(query));
        if (queryTokens.isEmpty()) {
            return true;
        }

        String normalizedSearchText = normalizeText(searchText);
        for (String candidateName : normalizedSearchText.split("\\R", -1)) {
            if (containsOrderedTokens(tokenize(candidateName), queryTokens)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts every candidate with {@link Locale#ROOT} while retaining list order and duplicate names.
     *
     * @param candidates names to normalize
     * @return normalized candidates in the original order
     * @throws NullPointerException if the collection or any candidate is {@code null}
     */
    private static List<String> normalize(List<String> candidates) {
        ArrayList<String> normalizedCandidates = new ArrayList<>(candidates.size());
        for (String candidate : candidates) {
            normalizedCandidates.add(candidate.toLowerCase(Locale.ROOT));
        }
        return normalizedCandidates;
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
     * @param candidateTokens tokens from one candidate name
     * @param queryTokens     normalized query tokens
     * @return whether every query token is contained by an ordered candidate-token subsequence
     */
    private static boolean containsOrderedTokens(List<String> candidateTokens, List<String> queryTokens) {
        int candidateIndex = 0;
        for (String queryToken : queryTokens) {
            while (candidateIndex < candidateTokens.size() &&
                    !candidateTokens.get(candidateIndex).contains(queryToken)) {
                candidateIndex++;
            }
            if (candidateIndex == candidateTokens.size()) {
                return false;
            }
            candidateIndex++;
        }
        return true;
    }

    /**
     * Produces the combined scope with every input candidate preceding every output candidate.
     *
     * @param inputs  normalized input names
     * @param outputs normalized output names
     * @return both groups joined by newlines
     */
    private static String joinInputsAndOutputs(List<String> inputs, List<String> outputs) {
        ArrayList<String> combined = new ArrayList<>(Math.addExact(inputs.size(), outputs.size()));
        combined.addAll(inputs);
        combined.addAll(outputs);
        return String.join("\n", combined);
    }
}
