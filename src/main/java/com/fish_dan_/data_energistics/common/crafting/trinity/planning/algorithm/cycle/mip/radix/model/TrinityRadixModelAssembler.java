package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds.TrinityCycleObjectiveBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityFiringBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixCodec;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixLinearEncoder;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixVariable;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template.TrinityMipCoefficientTemplate.Coefficient;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        if (codec == null || exactBounds == null) {
            throw new IllegalArgumentException("A Trinity radix assembler requires a codec and exact bounds");
        }
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
        if (request == null || pass == null || logicalUpper == null || logicalUpper.signum() < 0) {
            throw new IllegalArgumentException("A Trinity radix assembly request is incomplete");
        }
        TrinityRadixLinearEncoder model = new TrinityRadixLinearEncoder(this.codec);
        LinkedHashMap<TrinityPatternVariant, TrinityRadixVariable> firingVariables = new LinkedHashMap<>();
        boolean overflowProofDomain = logicalUpper.compareTo(TrinityFiringBounds.MAXIMUM_FIRINGS) > 0 &&
                request.fullLongFiringDomain();
        for (int index = 0; index < request.variants().size(); index++) {
            TrinityPatternVariant variant = request.variants().get(index);
            TrinityFiringBounds bounds = request.firingBounds().get(variant);
            TrinityRadixVariable variable = model.addBounded(
                    "firing_" + index,
                    overflowProofDomain ? logicalUpper : logicalUpper.min(bounds.upperInclusive()),
                    false);
            if (bounds.lowerInclusive().signum() > 0) {
                model.addLowerBound("firing_lower_" + index, variable, bounds.lowerInclusive());
            }
            firingVariables.put(variant, variable);
        }
        LinkedHashMap<AEKey, TrinityRadixVariable> seedVariables = reserveVariables(
                model,
                request.internalKeys(),
                request,
                "seed_",
                logicalUpper);
        LinkedHashMap<AEKey, TrinityRadixVariable> externalVariables = reserveVariables(
                model,
                this.exactBounds.externalReserveKeys(request),
                request,
                "external_",
                logicalUpper);
        LinkedHashMap<AEKey, TrinityRadixVariable> actualVariables = new LinkedHashMap<>();
        LinkedHashMap<AEKey, TrinityRadixVariable> missingVariables = new LinkedHashMap<>();
        if (request.shortageDiagnostic()) {
            LinkedHashSet<AEKey> finiteKeys = new LinkedHashSet<>(request.internalKeys());
            finiteKeys.addAll(this.exactBounds.externalReserveKeys(request));
            finiteKeys.removeAll(request.producibleInputs());
            int actualIndex = 0;
            int missingIndex = 0;
            int splitIndex = 0;
            for (AEKey key : finiteKeys) {
                BigInteger requiredUpper = this.exactBounds.reserveUpperBound(request, key, logicalUpper);
                TrinityRadixVariable actual = model.addBounded(
                        "actual_" + actualIndex++,
                        request.available().getOrDefault(key, BigInteger.ZERO).min(requiredUpper),
                        true);
                TrinityRadixVariable missing = model.addBounded(
                        "missing_" + missingIndex++,
                        requiredUpper,
                        true);
                actualVariables.put(key, actual);
                missingVariables.put(key, missing);
                TrinityRadixVariable required = request.internalKeys().contains(key) ?
                        seedVariables.get(key) : externalVariables.get(key);
                model.addExact(
                        "shortage_split_" + splitIndex++,
                        Map.of(
                                required, BigInteger.ONE,
                                actual, BigInteger.ONE.negate(),
                                missing, BigInteger.ONE.negate()),
                        BigInteger.ZERO);
            }
        }
        addConservation(model, request, firingVariables, seedVariables, externalVariables);

        TrinityRadixVariable seedTotal = model.addTotal("seed_total", seedVariables.values());
        TrinityRadixVariable externalTotal = model.addTotal("external_total", externalVariables.values());
        TrinityRadixVariable firingTotal = model.addTotal("firing_total", firingVariables.values());
        TrinityRadixVariable missingTotal = request.shortageDiagnostic() ?
                model.addTotal("missing_total", missingVariables.values()) : firingTotal;
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
                seedVariables,
                externalVariables,
                seedTotal,
                externalTotal,
                firingTotal,
                missingTotal,
                minimumSeed,
                minimumExternal);
        model.checkSize();
        return new TrinityRadixBuiltModel(
                model,
                firingVariables,
                seedVariables,
                externalVariables,
                actualVariables,
                missingVariables,
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
                                               Map<AEKey, TrinityRadixVariable> seedVariables,
                                               Map<AEKey, TrinityRadixVariable> externalVariables,
                                               TrinityRadixVariable seedTotal,
                                               TrinityRadixVariable externalTotal,
                                               TrinityRadixVariable firingTotal,
                                               TrinityRadixVariable missingTotal,
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
            case TrinityRadixModelPass.ShortageMissing.INSTANCE -> new ObjectiveSelection(
                    missingTotal,
                    true,
                    BigInteger.ZERO,
                    missingTotal.upperBound());
            case TrinityRadixModelPass.ShortageExternal(var fixedMissing) -> {
                model.addFixed("fixed_missing", missingTotal, fixedMissing);
                BigInteger lower = request.fixedExternalTotal().orElse(minimumExternal);
                BigInteger upper = request.fixedExternalTotal().orElse(externalTotal.upperBound());
                yield new ObjectiveSelection(externalTotal, true, lower, upper);
            }
            case TrinityRadixModelPass.ShortageSeed(var fixedMissing, var fixedExternal, var seedLowerBound) -> {
                model.addFixed("fixed_missing", missingTotal, fixedMissing);
                model.addFixed("fixed_external", externalTotal, fixedExternal);
                BigInteger seedSearchLower = minimumSeed.max(request.seedLowerBound()).max(seedLowerBound);
                model.addLowerBound("shortage_seed_search_lower", seedTotal, seedSearchLower);
                yield new ObjectiveSelection(seedTotal, true, seedSearchLower, seedTotal.upperBound());
            }
            case TrinityRadixModelPass.ShortageFiring(var fixedMissing, var fixedExternal, var fixedSeed, var firingLowerBound) -> {
                model.addFixed("fixed_missing", missingTotal, fixedMissing);
                model.addFixed("fixed_external", externalTotal, fixedExternal);
                model.addFixed("fixed_seed", seedTotal, fixedSeed);
                BigInteger firingSearchLower = firingLowerBound.max(request.firingLowerBound()).max(
                        this.exactBounds.conservationFiringLowerBound(
                                request,
                                fixedExternal,
                                fixedSeed));
                model.addLowerBound("shortage_firing_search_lower", firingTotal, firingSearchLower);
                yield new ObjectiveSelection(firingTotal, true, firingSearchLower, firingTotal.upperBound());
            }
            case TrinityRadixModelPass.ShortageIdentity(var fixedMissing, var fixedExternal, var fixedSeed, var fixedFirings, var fixedCounts, var variant) -> {
                model.addFixed("fixed_missing", missingTotal, fixedMissing);
                model.addFixed("fixed_external", externalTotal, fixedExternal);
                model.addFixed("fixed_seed", seedTotal, fixedSeed);
                model.addFixed("fixed_firings", firingTotal, fixedFirings);
                fixedCounts.forEach((fixedVariant, count) -> model.addFixed(
                        "fixed_shortage_firing_" + request.variants().indexOf(fixedVariant),
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
                    throw new TrinityRadixInfeasibleException("shortage_identity_objective_upper");
                }
                yield new ObjectiveSelection(objective, false, BigInteger.ZERO, upperBound);
            }
            case TrinityRadixModelPass.ShortageReserve(var fixedMissing, var fixedExternal, var fixedSeed, var fixedFirings, var fixedCounts, var fixedReserves, var key) -> {
                model.addFixed("fixed_missing", missingTotal, fixedMissing);
                model.addFixed("fixed_external", externalTotal, fixedExternal);
                model.addFixed("fixed_seed", seedTotal, fixedSeed);
                model.addFixed("fixed_firings", firingTotal, fixedFirings);
                fixedCounts.forEach((fixedVariant, count) -> model.addFixed(
                        "fixed_reserve_firing_" + request.variants().indexOf(fixedVariant),
                        firingVariables.get(fixedVariant),
                        count));
                int reserveIndex = 0;
                for (Map.Entry<AEKey, BigInteger> fixed : fixedReserves.entrySet()) {
                    model.addFixed(
                            "fixed_reserve_" + reserveIndex++,
                            requiredVariable(fixed.getKey(), seedVariables, externalVariables),
                            fixed.getValue());
                }
                TrinityRadixVariable objective = requiredVariable(key, seedVariables, externalVariables);
                yield new ObjectiveSelection(objective, true, BigInteger.ZERO, objective.upperBound());
            }
        };
    }

    private static TrinityRadixVariable requiredVariable(
                                                         AEKey key,
                                                         Map<AEKey, TrinityRadixVariable> seedVariables,
                                                         Map<AEKey, TrinityRadixVariable> externalVariables) {
        TrinityRadixVariable variable = seedVariables.get(key);
        if (variable == null) {
            variable = externalVariables.get(key);
        }
        if (variable == null) {
            throw new IllegalArgumentException("A Trinity shortage reserve key has no radix model axis");
        }
        return variable;
    }

    private LinkedHashMap<AEKey, TrinityRadixVariable> reserveVariables(
                                                                        TrinityRadixLinearEncoder model,
                                                                        Set<AEKey> keys,
                                                                        TrinityCycleFeasibilityRequest request,
                                                                        String prefix,
                                                                        BigInteger logicalUpper) {
        LinkedHashMap<AEKey, TrinityRadixVariable> variables = new LinkedHashMap<>();
        int index = 0;
        for (AEKey key : keys) {
            BigInteger upper = this.exactBounds.reserveUpperBound(
                    request,
                    key,
                    logicalUpper);
            variables.put(key, model.addBounded(prefix + index++, upper, !request.producibleInputs().contains(key)));
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
            LinkedHashMap<TrinityRadixVariable, BigInteger> terms = netTerms(request, key, firingVariables);
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
            String name = "settled_internal_" + settlementIndex++;
            LinkedHashMap<TrinityRadixVariable, BigInteger> terms = netTerms(request, key, firingVariables);
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

    private static LinkedHashMap<TrinityRadixVariable, BigInteger> netTerms(
                                                                            TrinityCycleFeasibilityRequest request,
                                                                            AEKey key,
                                                                            Map<TrinityPatternVariant, TrinityRadixVariable> firings) {
        LinkedHashMap<TrinityRadixVariable, BigInteger> terms = new LinkedHashMap<>();
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
