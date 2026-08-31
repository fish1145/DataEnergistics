package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology;

import com.fish_dan_.data_energistics.common.crafting.trinity.pattern.binding.TrinityPatternBindingEnumerator;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityBoundPatternInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;

import net.minecraft.network.chat.Component;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLists;

import java.math.BigInteger;
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
     * @param maxVariants hard cap for one Cartesian product and for all deduplicated variants materialized across the
     *                    request graph
     * @return complete identity-ordered variants or {@code VARIANT_LIMIT}
     */
    public TrinityAlgorithmResult<List<TrinityPatternVariant>> expand(
                                                                      TrinityCraftingGraphSnapshot snapshot,
                                                                      int maxVariants) {
        return expand(snapshot, maxVariants, TrinityPlanningControl.unbounded());
    }

    /**
     * Expands bindings while observing the request-wide cancellation and deadline boundary.
     */
    public TrinityAlgorithmResult<List<TrinityPatternVariant>> expand(
                                                                      TrinityCraftingGraphSnapshot snapshot,
                                                                      int maxVariants,
                                                                      TrinityPlanningControl control) {
        if (snapshot == null || maxVariants <= 0 || control == null) {
            throw new IllegalArgumentException(
                    "A Trinity variant expansion requires a snapshot and a positive variant limit");
        }

        BigInteger limit = BigInteger.valueOf(maxVariants);
        BigInteger materializedVariants = BigInteger.ZERO;
        ObjectArrayList<TrinityPatternVariant> variants = new ObjectArrayList<>();
        for (TrinityCraftingGraphPattern pattern : snapshot.patterns()) {
            TrinityAlgorithmResult<List<TrinityPatternVariant>> expanded = expandPattern(
                    pattern,
                    maxVariants,
                    control);
            if (!expanded.successful()) {
                return expanded;
            }
            BigInteger patternVariants = BigInteger.valueOf(expanded.value().size());
            materializedVariants = materializedVariants.add(patternVariants);
            if (materializedVariants.compareTo(limit) > 0) {
                return variantLimit(pattern, maxVariants, materializedVariants);
            }
            variants.addAll(expanded.value());
        }
        return TrinityAlgorithmResult.success(ObjectLists.unmodifiable(variants));
    }

    /**
     * Expands one semantic pattern independently so completed expansion can be reused by other target closures.
     */
    public TrinityAlgorithmResult<List<TrinityPatternVariant>> expandPattern(
                                                                             TrinityCraftingGraphPattern pattern,
                                                                             int maxVariants,
                                                                             TrinityPlanningControl control) {
        StopState state = stopState(control);
        if (state != StopState.RUNNING) {
            return stopped(state);
        }
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
        ObjectArrayList<TrinityPatternVariant> variants = new ObjectArrayList<>(bindings.size());
        expandPattern(pattern, bindings, variants);
        return TrinityAlgorithmResult.success(ObjectLists.unmodifiable(variants));
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

    private static StopState stopState(TrinityPlanningControl control) {
        if (control.cancellationRequested()) {
            return StopState.CANCELLED;
        }
        return control.deadlineExceeded() ? StopState.DEADLINE_EXCEEDED : StopState.RUNNING;
    }

    private static <T> TrinityAlgorithmResult<T> stopped(StopState state) {
        return switch (state) {
            case CANCELLED -> TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic.cancelled"),
                    Map.of("phase", "variant_expansion")));
            case DEADLINE_EXCEEDED -> TrinityAlgorithmResult.failure(new TrinityPlanningDiagnostic(
                    TrinityPlanningDiagnosticCode.MIP_TIMEOUT,
                    Component.translatable("gui.data_energistics.trinity_planning.diagnostic.timeout"),
                    Map.of("phase", "variant_expansion")));
            case RUNNING -> throw new IllegalArgumentException("A running Trinity variant expansion is not stopped");
        };
    }

    private static void expandPattern(TrinityCraftingGraphPattern pattern,
                                      List<TrinityPatternBindingEnumerator.Binding> enumeratedBindings,
                                      List<TrinityPatternVariant> destination) {
        for (TrinityPatternBindingEnumerator.Binding enumerated : enumeratedBindings) {
            ObjectArrayList<TrinityBoundPatternInput> bindings = new ObjectArrayList<>(pattern.inputs().size());
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

    private enum StopState {
        RUNNING,
        CANCELLED,
        DEADLINE_EXCEEDED
    }
}
