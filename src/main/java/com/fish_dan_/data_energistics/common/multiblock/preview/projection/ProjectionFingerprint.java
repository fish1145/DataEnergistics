package com.fish_dan_.data_energistics.common.multiblock.preview.projection;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewPredicateKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewSelection;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Structured deterministic identity of every selection field that can change an ordinary recipe projection.
 *
 * @param controllerId        controller-level owner identity
 * @param definitionRevision  active definition generation
 * @param structureKey        active named structure identity
 * @param variantIndex        shape variant inside the named structure
 * @param repeatCounts        selected repeat count per pattern unit
 * @param tierSelections      selected value per stable tier domain
 * @param candidateSelections selected candidate per unexpanded predicate coordinate
 */
public record ProjectionFingerprint(ResourceLocation controllerId,
                                    long definitionRevision,
                                    JsonMultiBlockStructureKey structureKey,
                                    int variantIndex,
                                    List<Integer> repeatCounts,
                                    Map<String, Integer> tierSelections,
                                    Map<PreviewPredicateKey, Integer> candidateSelections) {

    private static final Comparator<PreviewPredicateKey> PREDICATE_ORDER = Comparator
            .comparingInt(PreviewPredicateKey::sourceLayer)
            .thenComparingInt(PreviewPredicateKey::y)
            .thenComparingInt(PreviewPredicateKey::x);

    public ProjectionFingerprint {
        if (controllerId == null || structureKey == null) {
            throw new IllegalArgumentException("Projection fingerprint identities cannot be null");
        }
        if (!controllerId.equals(structureKey.machineId())) {
            throw new IllegalArgumentException("Projection fingerprint structure does not belong to its controller");
        }
        if (definitionRevision < 0L) {
            throw new IllegalArgumentException("Projection fingerprint revision cannot be negative: " +
                    definitionRevision);
        }
        SubstructureSelection selection = new SubstructureSelection(
                variantIndex,
                repeatCounts,
                tierSelections,
                candidateSelections);
        repeatCounts = selection.repeatCounts();
        tierSelections = sortedTiers(selection.tierSelections());
        candidateSelections = sortedCandidates(selection.candidateSelections());
    }

    /**
     * Captures the active recipe-affecting choices from a validated preview session.
     *
     * @param selection revision-bound session selection
     * @return deterministic active projection identity
     */
    public static ProjectionFingerprint from(PreviewSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("Projection fingerprint requires a preview selection");
        }
        SubstructureSelection active = selection.activeSelection();
        return new ProjectionFingerprint(
                selection.controllerId(),
                selection.definitionRevision(),
                selection.activeStructureKey(),
                active.variantIndex(),
                active.repeatCounts(),
                active.tierSelections(),
                active.candidateSelections());
    }

    private static Map<String, Integer> sortedTiers(Map<String, Integer> tiers) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(new TreeMap<>(tiers)));
    }

    private static Map<PreviewPredicateKey, Integer> sortedCandidates(
                                                                      Map<PreviewPredicateKey, Integer> candidates) {
        Map<PreviewPredicateKey, Integer> sorted = new TreeMap<>(PREDICATE_ORDER);
        sorted.putAll(candidates);
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }
}
