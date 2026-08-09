package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology;

import com.fish_dan_.data_energistics.common.crafting.trinity.pattern.binding.TrinityPatternBindingEnumerator;
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
 * Deterministically materializes legal Cartesian input bindings from an immutable crafting graph.
 * <p>
 * Exact transition expansion that collapses equivalent Cartesian bindings before applying its configured cap.
 */
public final class TrinityPatternVariantExpander {

    /**
     * @return stateless exact expander
     */
    public static TrinityPatternVariantExpander create() {
        return new TrinityPatternVariantExpander();
    }

    private final TrinityPatternBindingEnumerator bindingEnumerator = TrinityPatternBindingEnumerator.create();

    /**
     * @param snapshot    immutable graph revision
     * @param maxVariants hard cap for one Cartesian product and for additional binding branches across the graph;
     *                    every pattern's canonical first binding belongs to the base graph and does not consume this
     *                    budget
     * @return complete identity-ordered variants or {@code VARIANT_LIMIT}
     */
    public TrinityAlgorithmResult<List<TrinityPatternVariant>> expand(
                                                                      TrinityCraftingGraphSnapshot snapshot,
                                                                      int maxVariants) {
        if (snapshot == null || maxVariants <= 0) {
            throw new IllegalArgumentException(
                    "A Trinity variant expansion requires a snapshot and a positive variant limit");
        }

        BigInteger limit = BigInteger.valueOf(maxVariants);
        BigInteger additionalBranches = BigInteger.ZERO;
        ArrayList<PatternBindings> enumeratedPatterns = new ArrayList<>(snapshot.patterns().size());
        for (TrinityCraftingGraphPattern pattern : snapshot.patterns()) {
            TrinityPatternBindingEnumerator.Result enumeration = this.bindingEnumerator.enumerate(
                    pattern.inputs(),
                    maxVariants);
            List<TrinityPatternBindingEnumerator.Binding> bindings;
            switch (enumeration) {
                case TrinityPatternBindingEnumerator.LimitExceeded(var required, var limitValue) -> {
                    return variantLimit(pattern, limitValue, required);
                }
                case TrinityPatternBindingEnumerator.ArithmeticOverflow(var axis) -> {
                    return arithmeticOverflow(pattern, axis);
                }
                case TrinityPatternBindingEnumerator.Enumerated(var enumerated) -> bindings = enumerated;
            }
            BigInteger patternVariants = BigInteger.valueOf(bindings.size());
            additionalBranches = additionalBranches.add(patternVariants.subtract(BigInteger.ONE));
            if (additionalBranches.compareTo(limit) > 0) {
                return variantLimit(pattern, maxVariants, additionalBranches);
            }
            enumeratedPatterns.add(new PatternBindings(pattern, bindings));
        }

        int materializedCapacity;
        try {
            materializedCapacity = Math.addExact(snapshot.patterns().size(), additionalBranches.intValueExact());
        } catch (ArithmeticException overflow) {
            return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.ARITHMETIC_OVERFLOW,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic.arithmetic_overflow"),
                    Map.of(
                            "patterns", Integer.toString(snapshot.patterns().size()),
                            "additionalBranches", additionalBranches.toString())));
        }
        ArrayList<TrinityPatternVariant> variants = new ArrayList<>(materializedCapacity);
        for (PatternBindings pattern : enumeratedPatterns) {
            expandPattern(pattern.pattern(), pattern.bindings(), variants);
        }
        return TrinityAlgorithmResult.success(List.copyOf(variants));
    }

    private static TrinityAlgorithmResult<List<TrinityPatternVariant>> variantLimit(
                                                                                    TrinityCraftingGraphPattern pattern,
                                                                                    int maxVariants,
                                                                                    BigInteger required) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.VARIANT_LIMIT,
                Component.translatable("gui.data_energistics.trinity_planning.diagnostic.variant_limit"),
                Map.of(
                        "limit", Integer.toString(maxVariants),
                        "required", required.toString(),
                        "pattern", pattern.identity().publicationEncoding())));
    }

    private static TrinityAlgorithmResult<List<TrinityPatternVariant>> arithmeticOverflow(
                                                                                          TrinityCraftingGraphPattern pattern,
                                                                                          String axis) {
        return TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.ARITHMETIC_OVERFLOW,
                Component.translatable("gui.data_energistics.trinity_planning.diagnostic.arithmetic_overflow"),
                Map.of(
                        "pattern", pattern.identity().publicationEncoding(),
                        "axis", axis)));
    }

    private static void expandPattern(TrinityCraftingGraphPattern pattern,
                                      List<TrinityPatternBindingEnumerator.Binding> enumeratedBindings,
                                      List<TrinityPatternVariant> destination) {
        for (TrinityPatternBindingEnumerator.Binding enumerated : enumeratedBindings) {
            ArrayList<TrinityBoundPatternInput> bindings = new ArrayList<>(pattern.inputs().size());
            for (int slot = 0; slot < pattern.inputs().size(); slot++) {
                int alternativeIndex = enumerated.alternativeOrdinals().get(slot);
                TrinityPatternPublicationSignature.Input input = pattern.inputs().get(slot);
                TrinityPatternPublicationSignature.Alternative alternative = input.alternatives().get(alternativeIndex);
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
                    enumerated.cartesianOrdinal(),
                    enumerated.alternativeOrdinals(),
                    bindings,
                    pattern.outputs()));
        }
    }

    /**
     * Keeps one pattern coupled to its already bounded canonical bindings.
     */
    private record PatternBindings(
                                   TrinityCraftingGraphPattern pattern,
                                   List<TrinityPatternBindingEnumerator.Binding> bindings) {}
}
