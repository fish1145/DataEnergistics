package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch;

import appeng.api.networking.crafting.ICraftingSubmitResult;

import java.util.List;

/**
 * Filters and deterministically orders immutable crafting CPU facts before server-thread submission.
 */
public interface CraftingCpuCandidateSelector {

    /**
     * Creates the synchronous Phase 1 candidate policy.
     *
     * @return independent stateless selector
     */
    static CraftingCpuCandidateSelector create() {
        return new CraftingCpuCandidateSelectorImpl();
    }

    /**
     * Evaluates unavailable candidates and applies source, hardware, load, group cursor and stable-identity ordering.
     *
     * @param candidates complete explicitly supported candidate facts
     * @param request    immutable selection request
     * @return immutable ordered candidates and aggregate pre-submission diagnostics
     */
    CraftingCpuCandidateSelection evaluate(
                                           List<CraftingCpuCandidate> candidates,
                                           CraftingCpuSelectionRequest request);

    /**
     * Filters unavailable candidates and applies source, hardware, load, group cursor and stable-identity ordering.
     *
     * @param candidates complete explicitly supported candidate facts
     * @param request    immutable selection request
     * @return immutable ordered eligible candidates
     */
    default List<CraftingCpuCandidate> select(
                                              List<CraftingCpuCandidate> candidates,
                                              CraftingCpuSelectionRequest request) {
        return evaluate(candidates, request).candidates();
    }

    /**
     * Resolves the hardware-equivalent cursor group for one candidate and request source.
     *
     * @param candidate     immutable candidate facts
     * @param playerRequest whether the action source contains a player
     * @return independent successful-submission cursor group
     */
    CraftingCpuSelectionGroup group(CraftingCpuCandidate candidate, boolean playerRequest);

    /**
     * Returns whether a submission-time availability failure can safely continue without repeating ingredient
     * extraction.
     *
     * @param result failed submit result
     * @return whether the next ordered candidate may be attempted
     */
    boolean isRetryable(ICraftingSubmitResult result);
}
