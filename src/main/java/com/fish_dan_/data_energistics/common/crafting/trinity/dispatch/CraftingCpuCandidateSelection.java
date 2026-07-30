package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch;

import appeng.api.networking.crafting.UnsuitableCpus;

import java.util.List;

/**
 * Immutable result of evaluating explicitly supported CPU facts before any server-thread submission.
 *
 * @param candidates     deterministically ordered eligible candidates
 * @param unsuitableCpus aggregate reasons why known candidates were excluded from the attempt list
 */
public record CraftingCpuCandidateSelection(List<CraftingCpuCandidate> candidates,
                                            UnsuitableCpus unsuitableCpus) {

    public CraftingCpuCandidateSelection {
        if (candidates == null || unsuitableCpus == null) {
            throw new IllegalArgumentException("Crafting CPU selection candidates and diagnostics must not be null");
        }
        for (CraftingCpuCandidate candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("Crafting CPU selection candidates must not contain null");
            }
        }
        if (unsuitableCpus.offline() < 0 ||
                unsuitableCpus.busy() < 0 ||
                unsuitableCpus.tooSmall() < 0 ||
                unsuitableCpus.excluded() < 0) {
            throw new IllegalArgumentException("Crafting CPU unsuitable counts must not be negative");
        }
        candidates = List.copyOf(candidates);
    }

    /**
     * Returns whether at least one known candidate was excluded before submission.
     *
     * @return whether the diagnostic contains an unsuitable CPU
     */
    public boolean hasUnsuitableCpus() {
        return this.unsuitableCpus.offline() > 0 ||
                this.unsuitableCpus.busy() > 0 ||
                this.unsuitableCpus.tooSmall() > 0 ||
                this.unsuitableCpus.excluded() > 0;
    }
}
