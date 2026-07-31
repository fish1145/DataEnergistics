package com.fish_dan_.data_energistics.client.screen;

import java.util.List;

/**
 * Builds stable search text from localized pattern names for the Trinity access terminal.
 *
 * <p>
 * Separating text construction from pattern decoding lets the screen cache decoded names while this contract keeps
 * EAE-style search scope behavior directly testable.
 * </p>
 */
public interface TrinityPatternSearchMatcher {

    /**
     * Normalizes and joins the names selected by the requested search mode.
     *
     * @param inputs  localized input names in their display order
     * @param outputs localized output names in their display order
     * @param mode    scope that decides which names are included
     * @return lower-case candidate names separated by newlines, with inputs before outputs when both are selected
     * @throws NullPointerException if a collection, the mode, or any candidate name is {@code null}
     */
    String createSearchText(List<String> inputs, List<String> outputs, TrinityPatternSearchMode mode);

    /**
     * Checks whether one newline-delimited candidate name contains the query tokens in order.
     *
     * <p>
     * Candidate tokens may be skipped, but a query cannot consume tokens from multiple candidate names.
     * </p>
     *
     * @param searchText newline-delimited candidate names
     * @param query      user-entered search query
     * @return {@code true} for a blank query or when one candidate contains every query token in order
     * @throws NullPointerException if the search text or query is {@code null}
     */
    boolean matchesSearchText(String searchText, String query);
}
