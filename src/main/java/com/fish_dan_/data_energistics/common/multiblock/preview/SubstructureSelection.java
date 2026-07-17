package com.fish_dan_.data_energistics.common.multiblock.preview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable variant, repeat, tier, and predicate-candidate choices for one named substructure.
 *
 * @param variantIndex        zero-based shape variant inside the named structure
 * @param repeatCounts        one positive repeat count per MDLib pattern unit
 * @param tierSelections      positive values keyed by stable tier-domain id
 * @param candidateSelections non-negative candidate indexes keyed by unexpanded predicate coordinate
 */
public record SubstructureSelection(int variantIndex,
                                    List<Integer> repeatCounts,
                                    Map<String, Integer> tierSelections,
                                    Map<PreviewPredicateKey, Integer> candidateSelections) {

    /**
     * Copies all collections while validating values that do not require a structure definition.
     */
    public SubstructureSelection {
        if (variantIndex < 0) {
            throw new IllegalArgumentException("Preview variant index cannot be negative: " + variantIndex);
        }
        if (repeatCounts == null || tierSelections == null || candidateSelections == null) {
            throw new IllegalArgumentException("Substructure selection collections cannot be null");
        }
        repeatCounts = List.copyOf(repeatCounts);
        for (int repeatCount : repeatCounts) {
            if (repeatCount < 1) {
                throw new IllegalArgumentException("Preview repeat counts must be positive: " + repeatCount);
            }
        }
        tierSelections = immutableTierSelections(tierSelections);
        candidateSelections = immutableCandidateSelections(candidateSelections);
    }

    /**
     * Creates a selection for the default single-shape variant.
     */
    public SubstructureSelection(List<Integer> repeatCounts,
                                 Map<String, Integer> tierSelections,
                                 Map<PreviewPredicateKey, Integer> candidateSelections) {
        this(0, repeatCounts, tierSelections, candidateSelections);
    }

    /**
     * Replaces the shape variant without changing repeat, tier, or candidate choices.
     *
     * @param variantIndex zero-based shape variant inside the named structure
     * @return updated immutable selection
     */
    public SubstructureSelection withVariantIndex(int variantIndex) {
        return new SubstructureSelection(
                variantIndex,
                this.repeatCounts,
                this.tierSelections,
                this.candidateSelections);
    }

    /**
     * Replaces one unit repeat count without changing tier or candidate choices.
     *
     * @param unitIndex   MDLib pattern unit index
     * @param repeatCount positive replacement count
     * @return updated immutable selection
     */
    public SubstructureSelection withRepeat(int unitIndex, int repeatCount) {
        if (unitIndex < 0 || unitIndex >= this.repeatCounts.size()) {
            throw new IllegalArgumentException("Unknown preview repeat unit index: " + unitIndex);
        }
        if (repeatCount < 1) {
            throw new IllegalArgumentException("Preview repeat counts must be positive: " + repeatCount);
        }
        List<Integer> updated = new ArrayList<>(this.repeatCounts);
        updated.set(unitIndex, repeatCount);
        return new SubstructureSelection(this.variantIndex, updated, this.tierSelections, this.candidateSelections);
    }

    /**
     * Replaces one tier-domain value without changing other substructure state.
     *
     * @param domainId stable tier-domain id
     * @param value    positive replacement value
     * @return updated immutable selection
     */
    public SubstructureSelection withTier(String domainId, int value) {
        if (domainId == null || domainId.isBlank()) {
            throw new IllegalArgumentException("Preview tier domain id cannot be blank");
        }
        if (value < 1) {
            throw new IllegalArgumentException("Preview tier values must be positive: " + value);
        }
        if (!this.tierSelections.containsKey(domainId)) {
            throw new IllegalArgumentException("Unknown preview tier domain: " + domainId);
        }
        Map<String, Integer> updated = new LinkedHashMap<>(this.tierSelections);
        updated.put(domainId, value);
        return new SubstructureSelection(this.variantIndex, this.repeatCounts, updated, this.candidateSelections);
    }

    /**
     * Replaces the candidate index selected for one source predicate.
     *
     * @param predicateKey   unexpanded source predicate coordinate
     * @param candidateIndex non-negative candidate index
     * @return updated immutable selection
     */
    public SubstructureSelection withCandidate(PreviewPredicateKey predicateKey, int candidateIndex) {
        if (predicateKey == null) {
            throw new IllegalArgumentException("Preview candidate selection requires a predicate key");
        }
        if (candidateIndex < 0) {
            throw new IllegalArgumentException("Preview candidate index cannot be negative: " + candidateIndex);
        }
        Map<PreviewPredicateKey, Integer> updated = new LinkedHashMap<>(this.candidateSelections);
        updated.put(predicateKey, candidateIndex);
        return new SubstructureSelection(this.variantIndex, this.repeatCounts, this.tierSelections, updated);
    }

    private static Map<String, Integer> immutableTierSelections(Map<String, Integer> selections) {
        Map<String, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : selections.entrySet()) {
            String domainId = entry.getKey();
            Integer value = entry.getValue();
            if (domainId == null || domainId.isBlank()) {
                throw new IllegalArgumentException("Preview tier domain id cannot be blank");
            }
            if (value == null || value < 1) {
                throw new IllegalArgumentException("Preview tier values must be positive: " + value);
            }
            copy.put(domainId, value);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static Map<PreviewPredicateKey, Integer> immutableCandidateSelections(
                                                                                  Map<PreviewPredicateKey, Integer> selections) {
        Map<PreviewPredicateKey, Integer> copy = new LinkedHashMap<>();
        for (Map.Entry<PreviewPredicateKey, Integer> entry : selections.entrySet()) {
            PreviewPredicateKey predicateKey = entry.getKey();
            Integer candidateIndex = entry.getValue();
            if (predicateKey == null) {
                throw new IllegalArgumentException("Preview candidate selection requires a predicate key");
            }
            if (candidateIndex == null || candidateIndex < 0) {
                throw new IllegalArgumentException("Preview candidate index cannot be negative: " + candidateIndex);
            }
            copy.put(predicateKey, candidateIndex);
        }
        return Collections.unmodifiableMap(copy);
    }
}
