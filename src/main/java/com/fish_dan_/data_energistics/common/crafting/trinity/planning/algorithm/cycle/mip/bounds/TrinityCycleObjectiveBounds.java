package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Derives exact objective bounds and reserve domains shared by every Trinity cycle MIP representation.
 * <p>
 * BigInteger implementation of the logical limits used to certify sequential cycle objectives.
 */
public final class TrinityCycleObjectiveBounds {

    /**
     * @return stateless exact-bound calculator
     */
    public static TrinityCycleObjectiveBounds create() {
        return new TrinityCycleObjectiveBounds();
    }

    /**
     * Finds the mandatory first-firing SCC reserve floor.
     */
    public BigInteger minimumFirstInternalInput(TrinityCycleFeasibilityRequest request) {
        return request.variants().stream()
                .map(variant -> variant.inputs().entrySet().stream()
                        .filter(entry -> request.internalKeys().contains(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .reduce(BigInteger.ZERO, BigInteger::add))
                .filter(amount -> amount.signum() > 0)
                .min(BigInteger::compareTo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "A Trinity cycle variant must consume at least one internal key"));
    }

    /**
     * Finds the mandatory first-firing boundary reserve floor.
     */
    public BigInteger minimumFirstExternalInput(TrinityCycleFeasibilityRequest request) {
        return request.variants().stream()
                .map(variant -> variant.inputs().entrySet().stream()
                        .filter(entry -> !request.internalKeys().contains(entry.getKey()))
                        .map(Map.Entry::getValue)
                        .reduce(BigInteger.ZERO, BigInteger::add))
                .min(BigInteger::compareTo)
                .orElse(BigInteger.ZERO);
    }

    /**
     * Derives a conservation-based exact lower bound for total firings at fixed reserve objectives.
     */
    public BigInteger conservationFiringLowerBound(
                                                   TrinityCycleFeasibilityRequest request,
                                                   BigInteger fixedExternal,
                                                   BigInteger fixedSeed) {
        requireNonNegative(fixedExternal, "external");
        requireNonNegative(fixedSeed, "seed");
        BigInteger lowerBound = BigInteger.ZERO;
        LinkedHashSet<AEKey> touchedKeys = touchedKeys(request);
        Set<AEKey> externalKeys = externalReserveKeys(request);
        for (AEKey key : touchedKeys) {
            BigInteger reserveUpper = request.internalKeys().contains(key) ? fixedSeed :
                    externalKeys.contains(key) ? fixedExternal : BigInteger.ZERO;
            BigInteger deficit = request.demand().finalBalanceLowerBounds()
                    .getOrDefault(key, BigInteger.ZERO)
                    .subtract(reserveUpper);
            lowerBound = lowerBound.max(rowFiringLowerBound(request, key, deficit));
        }
        for (Map.Entry<AEKey, BigInteger> required : request.demand()
                .requiredNetChangeLowerBounds()
                .entrySet()) {
            lowerBound = lowerBound.max(rowFiringLowerBound(request, required.getKey(), required.getValue()));
        }
        return lowerBound.max(aggregateFiringLowerBound(
                request,
                touchedKeys,
                fixedSeed.add(fixedExternal)));
    }

    /**
     * Derives an exact necessary upper bound for one deterministic identity axis.
     */
    public BigInteger identityObjectiveUpperBound(
                                                  TrinityCycleFeasibilityRequest request,
                                                  BigInteger fixedExternal,
                                                  BigInteger fixedSeed,
                                                  BigInteger fixedFirings,
                                                  Map<TrinityPatternVariant, BigInteger> fixedCounts,
                                                  TrinityPatternVariant variant) {
        requireNonNegative(fixedExternal, "external");
        requireNonNegative(fixedSeed, "seed");
        requireNonNegative(fixedFirings, "firings");
        if (fixedCounts == null || variant == null) {
            throw new IllegalArgumentException("A Trinity identity objective requires fixed counts and a variant");
        }
        BigInteger remainingFirings = fixedFirings.subtract(total(fixedCounts));
        if (remainingFirings.signum() < 0) {
            throw new IllegalStateException("Fixed Trinity identity counts exceed the total firing objective");
        }
        List<TrinityPatternVariant> otherVariants = request.variants().stream()
                .filter(candidate -> !candidate.equals(variant))
                .filter(candidate -> !fixedCounts.containsKey(candidate))
                .toList();
        if (otherVariants.isEmpty()) {
            return remainingFirings;
        }

        BigInteger upper = remainingFirings;
        Set<AEKey> externalKeys = externalReserveKeys(request);
        for (AEKey key : touchedKeys(request)) {
            BigInteger reserveUpper = request.internalKeys().contains(key) ? fixedSeed :
                    externalKeys.contains(key) ? fixedExternal : BigInteger.ZERO;
            upper = upper.min(identityRowUpperBound(
                    fixedCounts,
                    variant,
                    otherVariants,
                    key,
                    request.demand().finalBalanceLowerBounds().getOrDefault(key, BigInteger.ZERO),
                    reserveUpper,
                    remainingFirings));
        }
        for (Map.Entry<AEKey, BigInteger> required : request.demand().requiredNetChangeLowerBounds().entrySet()) {
            upper = upper.min(identityRowUpperBound(
                    fixedCounts,
                    variant,
                    otherVariants,
                    required.getKey(),
                    required.getValue(),
                    BigInteger.ZERO,
                    remainingFirings));
        }
        return upper;
    }

    /**
     * Identifies boundary keys that may receive an initial external reserve variable.
     */
    public Set<AEKey> externalReserveKeys(TrinityCycleFeasibilityRequest request) {
        LinkedHashSet<AEKey> keys = new LinkedHashSet<>();
        request.variants().forEach(variant -> variant.inputs().keySet().stream()
                .filter(key -> !request.internalKeys().contains(key))
                .forEach(keys::add));
        request.demand().finalBalanceLowerBounds().keySet().stream()
                .filter(key -> !request.internalKeys().contains(key))
                .forEach(keys::add);
        return Collections.unmodifiableSet(keys);
    }

    /**
     * Captures finite current-inventory caps for exact post-solve conservation replay.
     */
    public Map<AEKey, BigInteger> finiteInputUpperBounds(TrinityCycleFeasibilityRequest request) {
        LinkedHashSet<AEKey> keys = new LinkedHashSet<>(request.internalKeys());
        request.variants().forEach(variant -> keys.addAll(variant.inputs().keySet()));
        keys.addAll(request.demand().finalBalanceLowerBounds().keySet());
        LinkedHashMap<AEKey, BigInteger> bounds = new LinkedHashMap<>();
        keys.stream()
                .filter(key -> !request.producibleInputs().contains(key))
                .forEach(key -> bounds.put(key, request.available().getOrDefault(key, BigInteger.ZERO)));
        return Collections.unmodifiableMap(bounds);
    }

    private static LinkedHashSet<AEKey> touchedKeys(TrinityCycleFeasibilityRequest request) {
        LinkedHashSet<AEKey> keys = new LinkedHashSet<>();
        request.variants().forEach(variant -> {
            keys.addAll(variant.inputs().keySet());
            keys.addAll(variant.outputs().keySet());
        });
        keys.addAll(request.demand().finalBalanceLowerBounds().keySet());
        keys.addAll(request.demand().requiredNetChangeLowerBounds().keySet());
        return keys;
    }

    private static BigInteger identityRowUpperBound(
                                                    Map<TrinityPatternVariant, BigInteger> fixedCounts,
                                                    TrinityPatternVariant variant,
                                                    List<TrinityPatternVariant> otherVariants,
                                                    AEKey key,
                                                    BigInteger rowLower,
                                                    BigInteger reserveUpper,
                                                    BigInteger remainingFirings) {
        BigInteger objectiveCoefficient = variant.netChange().getOrDefault(key, BigInteger.ZERO);
        BigInteger maximumOtherCoefficient = otherVariants.stream()
                .map(other -> other.netChange().getOrDefault(key, BigInteger.ZERO))
                .max(BigInteger::compareTo)
                .orElseThrow();
        if (objectiveCoefficient.compareTo(maximumOtherCoefficient) >= 0) {
            return remainingFirings;
        }
        BigInteger fixedContribution = fixedCounts.entrySet().stream()
                .map(entry -> entry.getKey().netChange()
                        .getOrDefault(key, BigInteger.ZERO)
                        .multiply(entry.getValue()))
                .reduce(BigInteger.ZERO, BigInteger::add);
        BigInteger numerator = fixedContribution
                .add(reserveUpper)
                .add(maximumOtherCoefficient.multiply(remainingFirings))
                .subtract(rowLower);
        if (numerator.signum() < 0) {
            throw new IllegalStateException("A feasible Trinity identity witness violated a conservation row");
        }
        return numerator.divide(maximumOtherCoefficient.subtract(objectiveCoefficient));
    }

    private static BigInteger rowFiringLowerBound(
                                                  TrinityCycleFeasibilityRequest request,
                                                  AEKey key,
                                                  BigInteger deficit) {
        if (deficit.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger maximumCoefficient = request.variants().stream()
                .map(variant -> variant.netChange().getOrDefault(key, BigInteger.ZERO))
                .max(BigInteger::compareTo)
                .orElseThrow();
        if (maximumCoefficient.signum() <= 0) {
            return BigInteger.ZERO;
        }
        return deficit.add(maximumCoefficient).subtract(BigInteger.ONE).divide(maximumCoefficient);
    }

    private static BigInteger aggregateFiringLowerBound(
                                                        TrinityCycleFeasibilityRequest request,
                                                        Set<AEKey> touchedKeys,
                                                        BigInteger fixedReserve) {
        BigInteger combinedBalance = touchedKeys.stream()
                .map(key -> request.demand().finalBalanceLowerBounds()
                        .getOrDefault(key, BigInteger.ZERO)
                        .max(request.demand().requiredNetChangeLowerBounds()
                                .getOrDefault(key, BigInteger.ZERO)))
                .reduce(BigInteger.ZERO, BigInteger::add)
                .subtract(fixedReserve);
        BigInteger requiredNet = request.demand().requiredNetChangeLowerBounds().values().stream()
                .reduce(BigInteger.ZERO, BigInteger::add)
                .subtract(fixedReserve);
        BigInteger finalBalance = request.demand().finalBalanceLowerBounds().values().stream()
                .reduce(BigInteger.ZERO, BigInteger::add)
                .subtract(fixedReserve);
        BigInteger deficit = combinedBalance.max(requiredNet).max(finalBalance);
        if (deficit.signum() <= 0) {
            return BigInteger.ZERO;
        }
        BigInteger maximumCoefficient = request.variants().stream()
                .map(variant -> touchedKeys.stream()
                        .map(key -> variant.netChange().getOrDefault(key, BigInteger.ZERO))
                        .reduce(BigInteger.ZERO, BigInteger::add))
                .max(BigInteger::compareTo)
                .orElseThrow();
        if (maximumCoefficient.signum() <= 0) {
            return BigInteger.ZERO;
        }
        return deficit.add(maximumCoefficient).subtract(BigInteger.ONE).divide(maximumCoefficient);
    }

    private static void requireNonNegative(BigInteger value, String role) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("A fixed Trinity " + role + " objective cannot be negative");
        }
    }

    private static BigInteger total(Map<?, BigInteger> amounts) {
        return amounts.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
    }
}
