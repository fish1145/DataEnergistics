package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Complete exact input for one sequential feasibility optimisation.
 *
 * @param variants           stable sorted firing axes
 * @param internalKeys       SCC keys whose initial balances are seed
 * @param demand             component-wide final and net lower bounds
 * @param available          current non-negative inventory
 * @param producibleInputs   predecessor-craftable inputs without a storage cap
 * @param firingBounds       complete stable per-variant firing domains
 * @param fixedExternalTotal exact external-input objective required by a branch, when present
 * @param seedLowerBound     lower bound used when advancing past an unschedulable seed level
 * @param firingLowerBound   lower bound used when advancing past an unschedulable firing level
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
                                             BigInteger firingLowerBound) {

    /**
     * Freezes every model input so background optimisation cannot observe mutation.
     */
    public TrinityCycleFeasibilityRequest {
        if (variants == null || variants.isEmpty() || internalKeys == null || internalKeys.isEmpty() ||
                demand == null || available == null || producibleInputs == null || firingBounds == null ||
                fixedExternalTotal == null ||
                seedLowerBound == null || seedLowerBound.signum() < 0 ||
                firingLowerBound == null || firingLowerBound.signum() < 0) {
            throw new IllegalArgumentException("A Trinity feasibility request requires complete non-negative bounds");
        }
        variants = variants.stream().sorted().toList();
        if (new LinkedHashSet<>(variants).size() != variants.size()) {
            throw new IllegalArgumentException("A Trinity feasibility request cannot repeat a pattern variant");
        }
        internalKeys = copyKeys(internalKeys, "internal");
        producibleInputs = copyKeys(producibleInputs, "producible");
        available = copyInventory(available);
        firingBounds = copyFiringBounds(variants, firingBounds);
        fixedExternalTotal = fixedExternalTotal.map(total -> {
            if (total.signum() < 0) {
                throw new IllegalArgumentException("A fixed Trinity external total cannot be negative");
            }
            return total;
        });
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
        LinkedHashSet<AEKey> boundedAxes = new LinkedHashSet<>(demand.finalBalanceLowerBounds().keySet());
        boundedAxes.addAll(demand.requiredNetChangeLowerBounds().keySet());
        LinkedHashSet<AEKey> protectedAxes = new LinkedHashSet<>(internalKeys);
        protectedAxes.addAll(boundedAxes);
        LinkedHashSet<AEKey> externalConsumedAxes = new LinkedHashSet<>();
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
                firingLowerBound);
    }

    private static Set<AEKey> copyKeys(Set<AEKey> source, String role) {
        LinkedHashSet<AEKey> copied = new LinkedHashSet<>();
        for (AEKey key : source) {
            if (key == null) {
                throw new IllegalArgumentException("A Trinity " + role + " key cannot be null");
            }
            copied.add(key);
        }
        return Collections.unmodifiableSet(copied);
    }

    private static Map<AEKey, BigInteger> copyInventory(Map<AEKey, BigInteger> source) {
        LinkedHashMap<AEKey, BigInteger> copied = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() < 0) {
                throw new IllegalArgumentException("A Trinity feasibility inventory amount cannot be negative");
            }
            if (amount.signum() > 0) {
                copied.put(key, amount);
            }
        });
        return Collections.unmodifiableMap(copied);
    }

    private static Map<TrinityPatternVariant, TrinityFiringBounds> copyFiringBounds(
                                                                                    List<TrinityPatternVariant> variants,
                                                                                    Map<TrinityPatternVariant, TrinityFiringBounds> source) {
        if (source.size() != variants.size() || !source.keySet().containsAll(variants)) {
            throw new IllegalArgumentException("Trinity firing bounds must cover the complete stable variant domain");
        }
        LinkedHashMap<TrinityPatternVariant, TrinityFiringBounds> copied = new LinkedHashMap<>();
        for (TrinityPatternVariant variant : variants) {
            TrinityFiringBounds bounds = source.get(variant);
            if (bounds == null) {
                throw new IllegalArgumentException("A Trinity firing bound cannot be null");
            }
            copied.put(variant, bounds);
        }
        return Collections.unmodifiableMap(copied);
    }
}
