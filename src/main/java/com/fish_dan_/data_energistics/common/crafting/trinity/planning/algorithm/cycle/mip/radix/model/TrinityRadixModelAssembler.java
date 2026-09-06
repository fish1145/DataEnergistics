package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds.TrinityCycleObjectiveBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixCodec;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixLinearEncoder;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixVariable;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template.TrinityMipCoefficientTemplate.Coefficient;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Assembles one bounded radix feasibility model from immutable logical inputs and one objective pass.
 * <p>
 * Builds normalized base-2^15 variables and conservation relations without invoking the solver.
 */
public final class TrinityRadixModelAssembler {

    /**
     * Creates an assembler sharing the exact codec and bound derivation used by objective probing.
     */
    public static TrinityRadixModelAssembler create(
                                                    TrinityRadixCodec codec,
                                                    TrinityCycleObjectiveBounds exactBounds) {
        return new TrinityRadixModelAssembler(codec, exactBounds);
    }

    private final TrinityRadixCodec codec;
    private final TrinityCycleObjectiveBounds exactBounds;

    TrinityRadixModelAssembler(TrinityRadixCodec codec, TrinityCycleObjectiveBounds exactBounds) {
        this.codec = codec;
        this.exactBounds = exactBounds;
    }

    /**
     * Builds all logical variables, conservation rows, carry columns, and certified objective limits.
     *
     * @param request      immutable SCC feasibility request
     * @param pass         current sequential lexicographic pass
     * @param logicalUpper upper bound for every logical axis in this representability domain
     */
    public TrinityRadixBuiltModel assemble(
                                           TrinityCycleFeasibilityRequest request,
                                           TrinityRadixModelPass pass,
                                           BigInteger logicalUpper) {
        if (logicalUpper.signum() < 0) {
            throw new IllegalArgumentException("A Trinity radix logical upper bound cannot be negative");
        }
        TrinityRadixLinearEncoder model = new TrinityRadixLinearEncoder(this.codec);
        Object2ObjectLinkedOpenHashMap<TrinityPatternVariant, TrinityRadixVariable> firingVariables = new Object2ObjectLinkedOpenHashMap<>();
        for (int index = 0; index < request.variants().size(); index++) {
            TrinityPatternVariant variant = request.variants().get(index);
            var bounds = request.firingBounds().get(variant);
            TrinityRadixVariable variable = model.addBounded(
                    "firing_" + index,
                    bounds.upperOr(logicalUpper),
                    false);
            if (bounds.lowerInclusive().signum() > 0) {
                model.addLowerBound("firing_lower_" + index, variable, bounds.lowerInclusive());
            }
            firingVariables.put(variant, variable);
        }
        Object2ObjectLinkedOpenHashMap<AEKey, TrinityRadixVariable> seedVariables = reserveVariables(
                model,
                request.internalKeys(),
                request,
                "seed_",
                logicalUpper);
        Object2ObjectLinkedOpenHashMap<AEKey, TrinityRadixVariable> externalVariables = reserveVariables(
                model,
                this.exactBounds.externalReserveKeys(request),
                request,
                "external_",
                logicalUpper);
        addConservation(model, request, firingVariables, seedVariables, externalVariables);

        TrinityRadixVariable seedTotal = model.addTotal("seed_total", seedVariables.values());
        TrinityRadixVariable externalTotal = model.addTotal("external_total", externalVariables.values());
        TrinityRadixVariable firingTotal = model.addTotal("firing_total", firingVariables.values());
        BigInteger minimumSeed = this.exactBounds.minimumFirstInternalInput(request);
        BigInteger minimumExternal = this.exactBounds.minimumFirstExternalInput(request);
        model.addLowerBound("seed_minimum", seedTotal, minimumSeed);
        model.addLowerBound("external_minimum", externalTotal, minimumExternal);
        request.fixedExternalTotal().ifPresent(fixed -> model.addFixed(
                "required_external",
                externalTotal,
                fixed));

        ObjectiveSelection selection = selectObjective(
                model,
                request,
                pass,
                firingVariables,
                seedTotal,
                externalTotal,
                firingTotal,
                minimumSeed,
                minimumExternal);
        model.checkSize();
        return new TrinityRadixBuiltModel(
                model,
                firingVariables,
                seedVariables,
                externalVariables,
                selection.objective(),
                selection.minimize(),
                selection.lowerBound(),
                selection.upperBound());
    }

    private ObjectiveSelection selectObjective(
                                               TrinityRadixLinearEncoder model,
                                               TrinityCycleFeasibilityRequest request,
                                               TrinityRadixModelPass pass,
                                               Map<TrinityPatternVariant, TrinityRadixVariable> firingVariables,
                                               TrinityRadixVariable seedTotal,
                                               TrinityRadixVariable externalTotal,
                                               TrinityRadixVariable firingTotal,
                                               BigInteger minimumSeed,
                                               BigInteger minimumExternal) {
        return switch (pass) {
            case TrinityRadixModelPass.External.INSTANCE -> {
                BigInteger lower = request.fixedExternalTotal().orElse(minimumExternal);
                BigInteger upper = request.fixedExternalTotal().orElse(externalTotal.upperBound());
                yield new ObjectiveSelection(externalTotal, true, lower, upper);
            }
            case TrinityRadixModelPass.Seed(var fixedExternal, var seedLowerBound) -> {
                model.addFixed("fixed_external", externalTotal, fixedExternal);
                BigInteger seedSearchLower = minimumSeed.max(seedLowerBound);
                model.addLowerBound("seed_search_lower", seedTotal, seedSearchLower);
                yield new ObjectiveSelection(seedTotal, true, seedSearchLower, seedTotal.upperBound());
            }
            case TrinityRadixModelPass.Firing(var fixedExternal, var fixedSeed, var firingLowerBound) -> {
                model.addFixed("fixed_external", externalTotal, fixedExternal);
                model.addFixed("fixed_seed", seedTotal, fixedSeed);
                BigInteger firingSearchLower = firingLowerBound.max(
                        this.exactBounds.conservationFiringLowerBound(
                                request,
                                fixedExternal,
                                fixedSeed));
                model.addLowerBound("firing_search_lower", firingTotal, firingSearchLower);
                yield new ObjectiveSelection(firingTotal, true, firingSearchLower, firingTotal.upperBound());
            }
            case TrinityRadixModelPass.Identity(var fixedExternal, var fixedSeed, var fixedFirings, var fixedCounts, var variant) -> {
                model.addFixed("fixed_external", externalTotal, fixedExternal);
                model.addFixed("fixed_seed", seedTotal, fixedSeed);
                model.addFixed("fixed_firings", firingTotal, fixedFirings);
                fixedCounts.forEach((fixedVariant, count) -> model.addFixed(
                        "fixed_firing_" + request.variants().indexOf(fixedVariant),
                        firingVariables.get(fixedVariant),
                        count));
                TrinityRadixVariable objective = firingVariables.get(variant);
                BigInteger upperBound = this.exactBounds.identityObjectiveUpperBound(
                        request,
                        fixedExternal,
                        fixedSeed,
                        fixedFirings,
                        fixedCounts,
                        variant)
                        .min(objective.upperBound());
                if (upperBound.signum() < 0) {
                    throw new TrinityRadixInfeasibleException("identity_objective_upper");
                }
                yield new ObjectiveSelection(objective, false, BigInteger.ZERO, upperBound);
            }
            case TrinityRadixModelPass.Feasibility.INSTANCE -> {
                model.addLowerBound("feasible_seed_lower", seedTotal, request.seedLowerBound());
                model.addLowerBound("feasible_firing_lower", firingTotal, request.firingLowerBound().max(BigInteger.ONE));
                yield new ObjectiveSelection(externalTotal, true, BigInteger.ZERO, externalTotal.upperBound());
            }
        };
    }

    private Object2ObjectLinkedOpenHashMap<AEKey, TrinityRadixVariable> reserveVariables(
                                                                                         TrinityRadixLinearEncoder model,
                                                                                         Set<AEKey> keys,
                                                                                         TrinityCycleFeasibilityRequest request,
                                                                                         String prefix,
                                                                                         BigInteger logicalUpper) {
        Object2ObjectLinkedOpenHashMap<AEKey, TrinityRadixVariable> variables = new Object2ObjectLinkedOpenHashMap<>();
        int index = 0;
        for (AEKey key : keys) {
            BigInteger upper = this.exactBounds.reserveUpperBound(
                    request,
                    key,
                    logicalUpper);
            variables.put(key, model.addBounded(prefix + index++, upper, !request.shortageDiagnostic() && !request.producibleInputs().contains(key)));
        }
        return variables;
    }

    private static void addConservation(
                                        TrinityRadixLinearEncoder model,
                                        TrinityCycleFeasibilityRequest request,
                                        Map<TrinityPatternVariant, TrinityRadixVariable> firingVariables,
                                        Map<AEKey, TrinityRadixVariable> seedVariables,
                                        Map<AEKey, TrinityRadixVariable> externalVariables) {
        ObjectArrayList<AEKey> touchedKeys = new ObjectArrayList<>(request.coefficientTemplate().touchedKeys());
        ObjectOpenHashSet<AEKey> seenKeys = new ObjectOpenHashSet<>(touchedKeys);
        request.demand().finalBalanceLowerBounds().keySet().forEach(key -> addStableKey(key, seenKeys, touchedKeys));
        request.demand().requiredNetChangeLowerBounds().keySet().forEach(
                key -> addStableKey(key, seenKeys, touchedKeys));
        int conservationIndex = 0;
        for (AEKey key : touchedKeys) {
            Object2ObjectLinkedOpenHashMap<TrinityRadixVariable, BigInteger> terms = netTerms(request, key, firingVariables);
            TrinityRadixVariable reserve = request.internalKeys().contains(key) ?
                    seedVariables.get(key) : externalVariables.get(key);
            if (reserve != null) {
                terms.merge(reserve, BigInteger.ONE, BigInteger::add);
            }
            model.addGreaterOrEqual(
                    "conservation_" + conservationIndex++,
                    terms,
                    request.demand().finalBalanceLowerBounds().getOrDefault(key, BigInteger.ZERO));
        }
        int netIndex = 0;
        for (Map.Entry<AEKey, BigInteger> bound : request.demand().requiredNetChangeLowerBounds().entrySet()) {
            model.addGreaterOrEqual(
                    "required_net_" + netIndex++,
                    netTerms(request, bound.getKey(), firingVariables),
                    bound.getValue());
        }
        int settlementIndex = 0;
        boolean exportsInternalKey = request.internalKeys().stream()
                .anyMatch(request.demand().requiredNetChangeLowerBounds()::containsKey);
        for (AEKey key : request.internalKeys()) {
            // Required net production is already constrained above; external supply may cover consumption, not output.
            if (request.producibleInputs().contains(key)) {
                continue;
            }
            String name = "settled_internal_" + settlementIndex++;
            Object2ObjectLinkedOpenHashMap<TrinityRadixVariable, BigInteger> terms = netTerms(request, key, firingVariables);
            BigInteger requestedOutput = request.demand().requiredNetChangeLowerBounds().get(key);
            if (requestedOutput != null) {
                model.addGreaterOrEqual(name, terms, requestedOutput);
            } else if (exportsInternalKey) {
                model.addExact(name, terms, BigInteger.ZERO);
            } else {
                model.addGreaterOrEqual(name, terms, BigInteger.ZERO);
            }
        }
    }

    private static Object2ObjectLinkedOpenHashMap<TrinityRadixVariable, BigInteger> netTerms(
                                                                                             TrinityCycleFeasibilityRequest request,
                                                                                             AEKey key,
                                                                                             Map<TrinityPatternVariant, TrinityRadixVariable> firings) {
        Object2ObjectLinkedOpenHashMap<TrinityRadixVariable, BigInteger> terms = new Object2ObjectLinkedOpenHashMap<>();
        for (Coefficient coefficient : request.coefficientTemplate().coefficients(key)) {
            TrinityPatternVariant variant = request.variants().get(coefficient.variantIndex());
            terms.put(firings.get(variant), coefficient.value());
        }
        return terms;
    }

    private static void addStableKey(AEKey key, Set<AEKey> seen, List<AEKey> destination) {
        if (seen.add(key)) {
            destination.add(key);
        }
    }

    /**
     * Keeps objective direction and exact certified bounds together after pass-specific assembly.
     */
    private record ObjectiveSelection(
                                      TrinityRadixVariable objective,
                                      boolean minimize,
                                      BigInteger lowerBound,
                                      BigInteger upperBound) {}
}
