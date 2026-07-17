package com.fish_dan_.data_energistics.common.multiblock.preview;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class PreviewSelectionTest {

    private static final PreviewPredicateKey PREDICATE_KEY = new PreviewPredicateKey(1, 0, 0);

    @Test
    void substructureSelectionCopiesInputsAndUsesCopyOnWriteHelpers() {
        List<Integer> repeats = new ArrayList<>(List.of(1, 2, 1));
        Map<String, Integer> tiers = new LinkedHashMap<>();
        tiers.put("core", 1);
        Map<PreviewPredicateKey, Integer> candidates = new LinkedHashMap<>();
        SubstructureSelection selection = new SubstructureSelection(repeats, tiers, candidates);

        repeats.set(1, 3);
        tiers.put("core", 2);
        candidates.put(PREDICATE_KEY, 2);

        SubstructureSelection varied = selection.withVariantIndex(2);
        SubstructureSelection repeated = varied.withRepeat(1, 3);
        SubstructureSelection tiered = repeated.withTier("core", 2);
        SubstructureSelection candidate = tiered.withCandidate(PREDICATE_KEY, 1);

        assertEquals(List.of(1, 2, 1), selection.repeatCounts());
        assertEquals(1, selection.tierSelections().get("core"));
        assertEquals(Map.of(), selection.candidateSelections());
        assertEquals(0, selection.variantIndex());
        assertEquals(2, candidate.variantIndex());
        assertEquals(List.of(1, 3, 1), candidate.repeatCounts());
        assertEquals(2, candidate.tierSelections().get("core"));
        assertEquals(1, candidate.candidateSelections().get(PREDICATE_KEY));
        assertThrows(IllegalArgumentException.class, () -> selection.withVariantIndex(-1));
    }
}
