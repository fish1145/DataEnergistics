package com.fish_dan_.data_energistics.blockentity.tower.equalization;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable two-phase FE transfer plan produced from one frozen tower snapshot.
 *
 * <p>
 * Source and sink totals are guaranteed to match, and one endpoint can appear on only one side of the plan. The
 * aggregate uses {@link BigInteger} because a tower may contain enough individually valid endpoints for their total
 * to exceed {@code long}.
 * </p>
 *
 * @param sources ordered positive withdrawals
 * @param sinks   ordered positive deposits
 */
public record TowerEnergyEqualizationPlan(List<TowerEnergySourceAllocation> sources,
                                          List<TowerEnergySinkAllocation> sinks) {

    /** Shared immutable result for snapshots that require no transfer. */
    private static final TowerEnergyEqualizationPlan EMPTY = new TowerEnergyEqualizationPlan(List.of(), List.of());

    /**
     * Defensively copies operations and verifies uniqueness and energy conservation.
     *
     * @param sources ordered positive withdrawals
     * @param sinks   ordered positive deposits
     */
    public TowerEnergyEqualizationPlan {
        sources = List.copyOf(sources);
        sinks = List.copyOf(sinks);

        Set<TowerEnergyEndpointId> sourceEndpoints = new HashSet<>();
        BigInteger sourceTotal = BigInteger.ZERO;
        for (TowerEnergySourceAllocation source : sources) {
            if (!sourceEndpoints.add(source.endpoint())) {
                throw new IllegalArgumentException("Plan contains duplicate source endpoint: " + source.endpoint());
            }
            sourceTotal = sourceTotal.add(BigInteger.valueOf(source.amount()));
        }

        Set<TowerEnergyEndpointId> sinkEndpoints = new HashSet<>();
        BigInteger sinkTotal = BigInteger.ZERO;
        for (TowerEnergySinkAllocation sink : sinks) {
            if (!sinkEndpoints.add(sink.endpoint())) {
                throw new IllegalArgumentException("Plan contains duplicate sink endpoint: " + sink.endpoint());
            }
            if (sourceEndpoints.contains(sink.endpoint())) {
                throw new IllegalArgumentException("Plan endpoint cannot be both source and sink: " + sink.endpoint());
            }
            sinkTotal = sinkTotal.add(BigInteger.valueOf(sink.amount()));
        }
        if (!sourceTotal.equals(sinkTotal)) {
            throw new IllegalArgumentException("Plan source and sink totals must be equal");
        }
    }

    /**
     * Returns the canonical plan with no transfer operations.
     *
     * @return immutable empty plan
     */
    public static TowerEnergyEqualizationPlan empty() {
        return EMPTY;
    }

    /**
     * Returns the conserved FE total transferred by this plan.
     *
     * @return sum of all source withdrawals and, equivalently, all sink deposits
     */
    public BigInteger totalAmount() {
        return this.sources.stream()
                .map(source -> BigInteger.valueOf(source.amount()))
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    /**
     * Returns whether the snapshot was already at its reachable proportional equilibrium.
     *
     * @return {@code true} when no source or sink operation is present
     */
    public boolean isEmpty() {
        return this.sources.isEmpty();
    }
}
