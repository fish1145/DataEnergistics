package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds.TrinityCycleObjectiveBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityFiringBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixCodec;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixLinearEncoder;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixVariable;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Builds normalized base-2^15 variables and conservation relations without invoking the solver.
 */
final class TrinityRadixModelAssemblerImpl implements TrinityRadixModelAssembler {

    private final TrinityRadixCodec codec;
    private final TrinityCycleObjectiveBounds exactBounds;

    TrinityRadixModelAssemblerImpl(TrinityRadixCodec codec, TrinityCycleObjectiveBounds exactBounds) {
        if (codec == null || exactBounds == null) {
            throw new IllegalArgumentException("A Trinity radix assembler requires a codec and exact bounds");
        }
        this.codec = codec;
        this.exactBounds = exactBounds;
    }

    @Override
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
        if (pass instanceof TrinityRadixModelPass.External) {
            BigInteger lower = request.fixedExternalTotal().orElse(minimumExternal);
            BigInteger upper = request.fixedExternalTotal().orElse(externalTotal.upperBound());
            return new ObjectiveSelection(
                    externalTotal,
                    true,
                    lower,
                    upper);
        }
        if (pass instanceof TrinityRadixModelPass.Seed seed) {
            model.addFixed("fixed_external", externalTotal, seed.fixedExternal());
            BigInteger seedSearchLower = minimumSeed.max(seed.seedLowerBound());
            model.addLowerBound("seed_search_lower", seedTotal, seedSearchLower);
            return new ObjectiveSelection(
                    seedTotal,
                    true,
                    seedSearchLower,
                    seedTotal.upperBound());
        }
        if (pass instanceof TrinityRadixModelPass.Firing firing) {
            model.addFixed("fixed_external", externalTotal, firing.fixedExternal());
            model.addFixed("fixed_seed", seedTotal, firing.fixedSeed());
            BigInteger firingSearchLower = firing.firingLowerBound().max(
                    this.exactBounds.conservationFiringLowerBound(
                            request,
                            firing.fixedExternal(),
                            firing.fixedSeed()));
            model.addLowerBound("firing_search_lower", firingTotal, firingSearchLower);
            return new ObjectiveSelection(
                    firingTotal,
                    true,
                    firingSearchLower,
                    firingTotal.upperBound());
        }
        if (pass instanceof TrinityRadixModelPass.Identity identity) {
            model.addFixed("fixed_external", externalTotal, identity.fixedExternal());
            model.addFixed("fixed_seed", seedTotal, identity.fixedSeed());
            model.addFixed("fixed_firings", firingTotal, identity.fixedFirings());
            identity.fixedCounts().forEach((variant, count) -> model.addFixed(
                    "fixed_firing_" + request.variants().indexOf(variant),
                    firingVariables.get(variant),
                    count));
            TrinityRadixVariable objective = firingVariables.get(identity.variant());
            BigInteger upperBound = this.exactBounds.identityObjectiveUpperBound(
                    request,
                    identity.fixedExternal(),
                    identity.fixedSeed(),
                    identity.fixedFirings(),
                    identity.fixedCounts(),
                    identity.variant())
                    .min(objective.upperBound());
            if (upperBound.signum() < 0) {
                throw new TrinityRadixInfeasibleException("identity_objective_upper");
            }
            return new ObjectiveSelection(objective, false, BigInteger.ZERO, upperBound);
        }
        throw new IllegalStateException("Unknown Trinity radix MIP pass");
    }

    private static LinkedHashMap<AEKey, TrinityRadixVariable> reserveVariables(
                                                                               TrinityRadixLinearEncoder model,
                                                                               Set<AEKey> keys,
                                                                               TrinityCycleFeasibilityRequest request,
                                                                               String prefix,
                                                                               BigInteger logicalUpper) {
        LinkedHashMap<AEKey, TrinityRadixVariable> variables = new LinkedHashMap<>();
        int index = 0;
        for (AEKey key : keys) {
            BigInteger upper = request.producibleInputs().contains(key) ?
                    logicalUpper : request.available().getOrDefault(key, BigInteger.ZERO);
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
        LinkedHashSet<AEKey> touchedKeys = new LinkedHashSet<>();
        request.variants().forEach(variant -> {
            touchedKeys.addAll(variant.inputs().keySet());
            touchedKeys.addAll(variant.outputs().keySet());
        });
        touchedKeys.addAll(request.demand().finalBalanceLowerBounds().keySet());
        touchedKeys.addAll(request.demand().requiredNetChangeLowerBounds().keySet());
        int conservationIndex = 0;
        for (AEKey key : touchedKeys) {
            LinkedHashMap<TrinityRadixVariable, BigInteger> terms = netTerms(key, firingVariables);
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
                    netTerms(bound.getKey(), firingVariables),
                    bound.getValue());
        }
    }

    private static LinkedHashMap<TrinityRadixVariable, BigInteger> netTerms(
                                                                            AEKey key,
                                                                            Map<TrinityPatternVariant, TrinityRadixVariable> firings) {
        LinkedHashMap<TrinityRadixVariable, BigInteger> terms = new LinkedHashMap<>();
        firings.forEach((variant, variable) -> {
            BigInteger coefficient = variant.netChange().getOrDefault(key, BigInteger.ZERO);
            if (coefficient.signum() != 0) {
                terms.put(variable, coefficient);
            }
        });
        return terms;
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
