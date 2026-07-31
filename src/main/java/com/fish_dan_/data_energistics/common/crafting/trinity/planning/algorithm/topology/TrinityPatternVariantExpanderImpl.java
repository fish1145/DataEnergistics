package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternPublicationSignature;

import net.minecraft.network.chat.Component;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Odometer-style Cartesian expansion that retains AE input order and stops before crossing its configured cap.
 */
final class TrinityPatternVariantExpanderImpl implements TrinityPatternVariantExpander {

    @Override
    public TrinityAlgorithmResult<List<TrinityPatternVariant>> expand(
                                                                      TrinityCraftingGraphSnapshot snapshot,
                                                                      int maxVariants) {
        if (snapshot == null || maxVariants <= 0) {
            throw new IllegalArgumentException(
                    "A Trinity variant expansion requires a snapshot and a positive variant limit");
        }

        BigInteger limit = BigInteger.valueOf(maxVariants);
        BigInteger total = BigInteger.ZERO;
        for (TrinityCraftingGraphPattern pattern : snapshot.patterns()) {
            BigInteger patternVariants = BigInteger.ONE;
            for (TrinityPatternPublicationSignature.Input input : pattern.inputs()) {
                patternVariants = patternVariants.multiply(BigInteger.valueOf(input.alternatives().size()));
            }
            total = total.add(patternVariants);
            if (total.compareTo(limit) > 0) {
                return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                        TrinityPlanningDiagnosticCode.VARIANT_LIMIT,
                        Component.literal("Trinity binding variants exceed the configured limit"),
                        Map.of(
                                "limit", Integer.toString(maxVariants),
                                "required", total.toString(),
                                "pattern", pattern.identity().publicationEncoding())));
            }
        }

        ArrayList<TrinityPatternVariant> variants = new ArrayList<>(total.intValueExact());
        for (TrinityCraftingGraphPattern pattern : snapshot.patterns()) {
            expandPattern(pattern, variants);
        }
        return TrinityAlgorithmResult.success(List.copyOf(variants));
    }

    private static void expandPattern(TrinityCraftingGraphPattern pattern,
                                      List<TrinityPatternVariant> destination) {
        int slotCount = pattern.inputs().size();
        if (slotCount == 0) {
            destination.add(TrinityPatternVariant.create(
                    pattern.identity(),
                    pattern.outputs().getFirst().what(),
                    0,
                    List.of(),
                    List.of(),
                    pattern.outputs()));
            return;
        }

        int[] alternatives = new int[slotCount];
        int ordinal = 0;
        boolean complete = false;
        while (!complete) {
            ArrayList<Integer> ordinals = new ArrayList<>(slotCount);
            ArrayList<TrinityBoundPatternInput> bindings = new ArrayList<>(slotCount);
            for (int slot = 0; slot < slotCount; slot++) {
                int alternativeIndex = alternatives[slot];
                TrinityPatternPublicationSignature.Input input = pattern.inputs().get(slot);
                TrinityPatternPublicationSignature.Alternative alternative = input.alternatives().get(alternativeIndex);
                ordinals.add(alternativeIndex);
                bindings.add(new TrinityBoundPatternInput(
                        slot,
                        alternativeIndex,
                        alternative.stack(),
                        input.multiplier(),
                        alternative.remainingKey()));
            }
            destination.add(TrinityPatternVariant.create(
                    pattern.identity(),
                    pattern.outputs().getFirst().what(),
                    ordinal,
                    ordinals,
                    bindings,
                    pattern.outputs()));
            ordinal++;
            complete = incrementOdometer(alternatives, pattern.inputs());
        }
    }

    private static boolean incrementOdometer(
                                             int[] ordinals,
                                             List<TrinityPatternPublicationSignature.Input> inputs) {
        for (int slot = ordinals.length - 1; slot >= 0; slot--) {
            ordinals[slot]++;
            if (ordinals[slot] < inputs.get(slot).alternatives().size()) {
                return false;
            }
            ordinals[slot] = 0;
        }
        return true;
    }
}
