package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template.TrinityMipCoefficientTemplate;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
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

    /**
     * Compatibility constructor for executable feasibility requests without virtual diagnostic input.
     */
    public TrinityCycleFeasibilityRequest(
                                          List<TrinityPatternVariant> variants,
                                          Set<AEKey> internalKeys,
                                          TrinityCycleDemand demand,
                                          Map<AEKey, BigInteger> available,
                                          Set<AEKey> producibleInputs,
                                          Map<TrinityPatternVariant, TrinityFiringBounds> firingBounds,
                                          Optional<BigInteger> fixedExternalTotal,
                                          BigInteger seedLowerBound,
                                          BigInteger firingLowerBound) {
        this(
                variants,
                internalKeys,
                demand,
                available,
                producibleInputs,
                firingBounds,
                fixedExternalTotal,
                seedLowerBound,
                firingLowerBound,
                false,
                0,
                TrinityMipCoefficientTemplate.create(variants, new ObjectArrayList<>(internalKeys)));
    }

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
            return Optional.empty();
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

    /**
     * @return whether every firing axis spans the complete downstream {@code long} domain
     */
    public boolean fullLongFiringDomain() {
        return firingBounds.values().stream().allMatch(bounds -> bounds.equals(TrinityFiringBounds.full()));
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

    /**
     * Creates a diagnostic-only request whose finite reserve can be split into actual and virtual missing input.
     * The returned request must never enter executable candidate search or scheduling. Its exactly verified solution
     * may
     * be scheduled only against a local synthetic inventory to produce non-executable diagnostic evidence.
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
