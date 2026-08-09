package com.fish_dan_.data_energistics.common.multiblock.preview.projection;

import com.fish_dan_.data_energistics.common.multiblock.json.definition.JsonMultiBlockStructureKey;
import com.fish_dan_.data_energistics.common.multiblock.preview.PreviewPredicateKey;

import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class ProjectionFingerprintTest {

    private static final ResourceLocation CONTROLLER = ResourceLocation.parse("data_energistics:fingerprint_test");
    private static final JsonMultiBlockStructureKey MAIN = new JsonMultiBlockStructureKey(CONTROLLER, "main");
    private static final PreviewPredicateKey FIRST_PREDICATE = new PreviewPredicateKey(0, 0, 1);
    private static final PreviewPredicateKey SECOND_PREDICATE = new PreviewPredicateKey(1, 0, 0);

    @Test
    void everyRecipeAffectingFieldChangesTheStructuredFingerprint() {
        ProjectionFingerprint base = fingerprint(
                CONTROLLER,
                4L,
                MAIN,
                0,
                List.of(1, 2),
                Map.of("core", 1),
                Map.of(FIRST_PREDICATE, 0));
        ResourceLocation otherController = ResourceLocation.parse("data_energistics:other_controller");

        assertNotEquals(base, fingerprint(
                otherController,
                4L,
                new JsonMultiBlockStructureKey(otherController, "main"),
                0,
                List.of(1, 2),
                Map.of("core", 1),
                Map.of(FIRST_PREDICATE, 0)));
        assertNotEquals(base, fingerprint(CONTROLLER, 5L, MAIN, 0, List.of(1, 2),
                Map.of("core", 1), Map.of(FIRST_PREDICATE, 0)));
        assertNotEquals(base, fingerprint(CONTROLLER, 4L,
                new JsonMultiBlockStructureKey(CONTROLLER, "cpu"), 0, List.of(1, 2),
                Map.of("core", 1), Map.of(FIRST_PREDICATE, 0)));
        assertNotEquals(base, fingerprint(CONTROLLER, 4L, MAIN, 1, List.of(1, 2),
                Map.of("core", 1), Map.of(FIRST_PREDICATE, 0)));
        assertNotEquals(base, fingerprint(CONTROLLER, 4L, MAIN, 0, List.of(1, 3),
                Map.of("core", 1), Map.of(FIRST_PREDICATE, 0)));
        assertNotEquals(base, fingerprint(CONTROLLER, 4L, MAIN, 0, List.of(1, 2),
                Map.of("core", 2), Map.of(FIRST_PREDICATE, 0)));
        assertNotEquals(base, fingerprint(CONTROLLER, 4L, MAIN, 0, List.of(1, 2),
                Map.of("core", 1), Map.of(FIRST_PREDICATE, 1)));
    }

    @Test
    void logicallyEquivalentMapsProduceOneDeterministicFingerprint() {
        Map<String, Integer> firstTiers = new LinkedHashMap<>();
        firstTiers.put("casing", 2);
        firstTiers.put("core", 1);
        Map<String, Integer> reversedTiers = new LinkedHashMap<>();
        reversedTiers.put("core", 1);
        reversedTiers.put("casing", 2);
        Map<PreviewPredicateKey, Integer> firstCandidates = new LinkedHashMap<>();
        firstCandidates.put(FIRST_PREDICATE, 0);
        firstCandidates.put(SECOND_PREDICATE, 1);
        Map<PreviewPredicateKey, Integer> reversedCandidates = new LinkedHashMap<>();
        reversedCandidates.put(SECOND_PREDICATE, 1);
        reversedCandidates.put(FIRST_PREDICATE, 0);

        ProjectionFingerprint first = fingerprint(
                CONTROLLER, 4L, MAIN, 0, List.of(1, 2), firstTiers, firstCandidates);
        ProjectionFingerprint reversed = fingerprint(
                CONTROLLER, 4L, MAIN, 0, List.of(1, 2), reversedTiers, reversedCandidates);

        assertEquals(first, reversed);
        assertEquals(first.hashCode(), reversed.hashCode());
        assertEquals(List.of("casing", "core"), List.copyOf(first.tierSelections().keySet()));
        assertEquals(List.of(FIRST_PREDICATE, SECOND_PREDICATE),
                List.copyOf(first.candidateSelections().keySet()));
        assertThrows(UnsupportedOperationException.class,
                () -> first.candidateSelections().put(FIRST_PREDICATE, 2));
    }

    @Test
    void structureMustBelongToTheController() {
        ResourceLocation otherController = ResourceLocation.parse("data_energistics:other_controller");

        assertThrows(IllegalArgumentException.class, () -> fingerprint(
                CONTROLLER,
                4L,
                new JsonMultiBlockStructureKey(otherController, "main"),
                0,
                List.of(1),
                Map.of(),
                Map.of()));
    }

    private static ProjectionFingerprint fingerprint(ResourceLocation controllerId,
                                                     long revision,
                                                     JsonMultiBlockStructureKey structureKey,
                                                     int variantIndex,
                                                     List<Integer> repeats,
                                                     Map<String, Integer> tiers,
                                                     Map<PreviewPredicateKey, Integer> candidates) {
        return new ProjectionFingerprint(
                controllerId,
                revision,
                structureKey,
                variantIndex,
                repeats,
                tiers,
                candidates);
    }
}
