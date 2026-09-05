package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template.TrinityMipCoefficientTemplate;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Complete exact input for one sequential feasibility optimisation.
 *
 * @param variants            stable sorted firing axes
 * @param internalKeys        SCC keys whose initial balances are seed
 * @param demand              component-wide final and net lower bounds
 * @param available           current non-negative inventory
 * @param producibleInputs    predecessor-craftable inputs without a storage cap
 * @param firingBounds        complete stable per-variant firing domains
 * @param fixedExternalTotal  exact external-input objective required by a branch, when present
 * @param seedLowerBound      lower bound used when advancing past an unschedulable seed level
 * @param firingLowerBound    lower bound used when advancing past an unschedulable firing level
 * @param shortageDiagnostic  whether finite reserve axes may use diagnostic-only virtual missing input
 * @param shortageStateLimit  maximum actual diagnostic solver calls, or zero for executable requests
 * @param coefficientTemplate immutable sparse transition layout shared across request-local models
 */
public record TrinityCycleFeasibilityRequest(
                                             List<TrinityPatternVariant> variants,
                                             Set<AEKey> internalKeys,
                                             TrinityCycleDemand demand,
                                             Map<AEKey, BigInteger> available,
                                             Set<AEKey> producibleInputs,
                                             Map<TrinityPatternVariant, TrinityFiringBounds> firingBounds,
                                             Optional<BigInteger> fixedExternalTotal,
                                             BigInteger seedLowerBound,
                                             BigInteger firingLowerBound,
                                             boolean shortageDiagnostic,
                                             int shortageStateLimit,
                                             TrinityMipCoefficientTemplate coefficientTemplate) {

    /** Uses the already normalized structural axes owned by the shared immutable template. */
    public TrinityCycleFeasibilityRequest {
        variants = coefficientTemplate.variants();
        internalKeys = coefficientTemplate.internalKeys();
    }

    /**
     * Derives a proof-carrying ordinary-model bound for monotone conservation systems. Every retained variant has a
     * positive integer contribution to a bounded demand axis and no negative contribution on an SCC or demand axis.
     * Negative changes on other keys are accepted only when no retained variant produces that key, making them
     * one-way external consumption with the model's positive reserve coefficient. Starting from a lexicographic
     * optimum, removing a firing that is redundant on every protected axis cannot worsen external input or seed; the
     * firing objective then selects an irredundant optimum whose size is bounded by the protected lower bounds. This is
     * an existence proof for a no-worse bounded optimum, not permission to remove an arbitrary firing from every
     * feasible vector. The largest complete first-firing input additionally bounds the mandatory initial-reserve floor.
     * Transient prefix seed remains the later exact scheduler's duty. Internally signed, externally produced, or
     * non-productive systems deliberately decline this fast path and use radix instead.
     *
     * @return finite logical-variable bound when monotonicity proves it, otherwise empty
     */
    public Optional<BigInteger> ordinaryLogicalUpperBound() {
        ObjectOpenHashSet<AEKey> boundedAxes = new ObjectOpenHashSet<>(demand.finalBalanceLowerBounds().keySet());
        boundedAxes.addAll(demand.requiredNetChangeLowerBounds().keySet());
        ObjectOpenHashSet<AEKey> protectedAxes = new ObjectOpenHashSet<>(internalKeys);
        protectedAxes.addAll(boundedAxes);
        ObjectOpenHashSet<AEKey> externalConsumedAxes = new ObjectOpenHashSet<>();
        variants.forEach(variant -> variant.netChange().forEach((key, amount) -> {
            if (!protectedAxes.contains(key) && amount.signum() < 0) {
                externalConsumedAxes.add(key);
            }
        }));
        boolean monotone = variants.stream().allMatch(variant -> protectedAxes.stream().allMatch(key -> variant.netChange().getOrDefault(key, BigInteger.ZERO).signum() >= 0) &&
                boundedAxes.stream().anyMatch(key -> variant.netChange().getOrDefault(key, BigInteger.ZERO).signum() > 0)) &&
                externalConsumedAxes.stream().allMatch(key -> variants.stream().allMatch(variant -> variant.netChange().getOrDefault(key, BigInteger.ZERO).signum() <= 0));
        if (!monotone) {
            return explicitLogicalUpperBound();
        }
        BigInteger demandBound = demand.finalBalanceLowerBounds().values().stream()
                .reduce(BigInteger.ZERO, BigInteger::add)
                .add(demand.requiredNetChangeLowerBounds().values().stream()
                        .reduce(BigInteger.ZERO, BigInteger::add));
        BigInteger firstFiringBound = variants.stream()
                .map(variant -> variant.inputs().values().stream()
                        .reduce(BigInteger.ZERO, BigInteger::add))
                .max(BigInteger::compareTo)
                .orElse(BigInteger.ONE);
        BigInteger baseBound = demandBound.max(BigInteger.ONE).add(firstFiringBound);
        BigInteger perVariantLower = firingBounds.values().stream()
                .map(TrinityFiringBounds::lowerInclusive)
                .reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger effectiveFiringLower = firingLowerBound.max(perVariantLower);
        return Optional.of(baseBound
                .add(effectiveFiringLower)
                .max(baseBound.add(seedLowerBound)));
    }

    private Optional<BigInteger> explicitLogicalUpperBound() {
        if (firingBounds.values().stream().anyMatch(bounds -> bounds.upperInclusive().isEmpty())) {
            return Optional.empty();
        }
        BigInteger upper = seedLowerBound.max(firingLowerBound);
        if (fixedExternalTotal.isPresent()) {
            upper = upper.max(fixedExternalTotal.orElseThrow());
        }
        for (BigInteger amount : demand.finalBalanceLowerBounds().values()) {
            upper = upper.max(amount);
        }
        for (BigInteger amount : demand.requiredNetChangeLowerBounds().values()) {
            upper = upper.max(amount);
        }
        ObjectOpenHashSet<AEKey> reserveKeys = new ObjectOpenHashSet<>(internalKeys);
        variants.forEach(variant -> reserveKeys.addAll(variant.inputs().keySet()));
        reserveKeys.addAll(demand.finalBalanceLowerBounds().keySet());
        for (TrinityPatternVariant variant : variants) {
            BigInteger variantUpper = firingBounds.get(variant).upperInclusive().orElseThrow();
            upper = upper.max(variantUpper);
        }
        for (AEKey key : reserveKeys) {
            BigInteger required = demand.finalBalanceLowerBounds().getOrDefault(key, BigInteger.ZERO);
            for (TrinityPatternVariant variant : variants) {
                BigInteger coefficient = variant.netChange().getOrDefault(key, BigInteger.ZERO);
                if (coefficient.signum() < 0) {
                    required = required.add(coefficient.negate().multiply(
                            firingBounds.get(variant).upperInclusive().orElseThrow()));
                }
            }
            upper = upper.max(required);
        }
        return Optional.of(upper.max(BigInteger.ONE));
    }

    /**
     * Replaces only firing domains while preserving all other immutable feasibility inputs.
     */
    public TrinityCycleFeasibilityRequest withFiringBounds(
                                                           Map<TrinityPatternVariant, TrinityFiringBounds> bounds) {
        return new TrinityCycleFeasibilityRequest(
                variants,
                internalKeys,
                demand,
                available,
                producibleInputs,
                bounds,
                fixedExternalTotal,
                seedLowerBound,
                firingLowerBound,
                shortageDiagnostic,
                shortageStateLimit,
                coefficientTemplate);
    }

    /** Caps only currently open firing axes for one request-private bounded feasibility model. */
    public TrinityCycleFeasibilityRequest withOpenFiringUpper(BigInteger upper) {
        Map<TrinityPatternVariant, TrinityFiringBounds> bounded = new Object2ObjectLinkedOpenHashMap<>();
        firingBounds.forEach((variant, bounds) -> bounded.put(
                variant,
                bounds.upperInclusive().isPresent() ? bounds : new TrinityFiringBounds(
                        bounds.lowerInclusive(),
                        upper.max(bounds.lowerInclusive()))));
        return withFiringBounds(bounded);
    }

    /**
     * Creates a diagnostic request with a bounded solver-call budget and virtual reserve for finite inputs.
     * Its candidate is not executable: scheduling must first validate it against a local synthetic inventory,
     * then compare the chosen order's exact inputs with real stock. Only a fully verified zero-shortage result
     * may return to executable planning; otherwise it remains diagnostic evidence.
     */
    public TrinityCycleFeasibilityRequest forShortageDiagnosis(int stateLimit) {
        return new TrinityCycleFeasibilityRequest(
                variants,
                internalKeys,
                demand,
                available,
                producibleInputs,
                firingBounds,
                fixedExternalTotal,
                seedLowerBound,
                firingLowerBound,
                true,
                stateLimit,
                coefficientTemplate);
    }
}
