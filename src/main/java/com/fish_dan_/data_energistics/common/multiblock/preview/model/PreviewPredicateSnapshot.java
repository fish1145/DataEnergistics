package com.fish_dan_.data_energistics.common.multiblock.preview.model;

import java.util.List;
import java.util.Optional;

/**
 * Immutable candidate view derived from one source predicate at a specific selection.
 *
 * @param key                    stable unexpanded source coordinate
 * @param role                   resolved cell role
 * @param candidates             ordered candidate choices after tier filtering
 * @param selectedCandidateIndex selected index, or {@code -1} for a wildcard
 */
public record PreviewPredicateSnapshot(PreviewPredicateKey key,
                                       PreviewCellRole role,
                                       List<PreviewCandidate> candidates,
                                       int selectedCandidateIndex) {

    /**
     * Copies candidates and rejects role/index combinations that could produce ambiguous materials.
     */
    public PreviewPredicateSnapshot {
        candidates = List.copyOf(candidates);
        if (role == PreviewCellRole.WILDCARD) {
            if (!candidates.isEmpty() || selectedCandidateIndex != -1) {
                throw new IllegalArgumentException("Wildcard preview predicates cannot expose a candidate selection");
            }
        } else if (candidates.isEmpty() || selectedCandidateIndex < 0 ||
                selectedCandidateIndex >= candidates.size()) {
                    throw new IllegalArgumentException("Preview predicate selected candidate index is outside its candidates");
                }
        validateRoleCandidates(role, candidates);
    }

    /**
     * Returns the selected candidate when this predicate is not a wildcard.
     */
    public Optional<PreviewCandidate> selectedCandidate() {
        if (this.selectedCandidateIndex < 0) {
            return Optional.empty();
        }
        return Optional.of(this.candidates.get(this.selectedCandidateIndex));
    }

    private static void validateRoleCandidates(PreviewCellRole role, List<PreviewCandidate> candidates) {
        long concreteCount = candidates.stream().filter(PreviewCandidate::concrete).count();
        long emptyCount = candidates.size() - concreteCount;
        switch (role) {
            case CONTROLLER, MATERIAL -> {
                if (concreteCount != candidates.size()) {
                    throw new IllegalArgumentException(role + " preview predicates require only concrete candidates");
                }
            }
            case OPTIONAL -> {
                if (concreteCount == 0L || emptyCount != 1L) {
                    throw new IllegalArgumentException(
                            "Optional preview predicates require concrete candidates and one empty choice");
                }
            }
            case AIR -> {
                if (candidates.size() != 1 || emptyCount != 1L) {
                    throw new IllegalArgumentException("Air preview predicates require exactly one empty choice");
                }
            }
            case WILDCARD -> {
                if (!candidates.isEmpty()) {
                    throw new IllegalArgumentException("Wildcard preview predicates cannot expose candidates");
                }
            }
        }
    }
}
