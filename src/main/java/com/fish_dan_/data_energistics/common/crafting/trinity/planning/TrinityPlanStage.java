package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * One dependency-addressable execution stage produced by DAG propagation or compressed cyclic scheduling.
 *
 * @param index           stable stage index
 * @param cycleStage      whether this stage belongs to a repeat block
 * @param dependencies    stages that must complete before this stage becomes ready
 * @param firings         compact pattern/variant firing vector
 * @param requiredAtStart minimum balances required before the stage starts
 * @param netChange       exact signed balance change after all firings complete
 */
public record TrinityPlanStage(
                               int index,
                               boolean cycleStage,
                               Set<Integer> dependencies,
                               List<TrinityPlanPatternFiring> firings,
                               Map<AEKey, BigInteger> requiredAtStart,
                               Map<AEKey, BigInteger> netChange) {

    /** Copies the execution surface and rejects ambiguous duplicate firing entries. */
    public TrinityPlanStage {
        if (index < 0 || dependencies == null || firings == null || firings.isEmpty()) {
            throw new IllegalArgumentException("A Trinity stage requires an index, dependencies and firings");
        }
        TreeSet<Integer> copiedDependencies = new TreeSet<>();
        for (Integer dependency : dependencies) {
            if (dependency == null || dependency < 0 || dependency == index) {
                throw new IllegalArgumentException("A Trinity stage dependency must reference another stage");
            }
            copiedDependencies.add(dependency);
        }
        dependencies = Collections.unmodifiableSet(copiedDependencies);
        firings = List.copyOf(firings);
        HashSet<String> bindings = new HashSet<>();
        for (TrinityPlanPatternFiring firing : firings) {
            if (firing == null) {
                throw new IllegalArgumentException("A Trinity stage cannot contain a null firing");
            }
            String binding = firing.patternIdentity().publicationEncoding() + '#' + firing.variantOrdinal();
            if (!bindings.add(binding)) {
                throw new IllegalArgumentException("A Trinity stage must aggregate duplicate pattern bindings");
            }
        }
        requiredAtStart = TrinityPlanAmounts.copyPositive(requiredAtStart, "stage start");
        netChange = TrinityPlanAmounts.copySignedNonZero(netChange, "stage net change");
    }
}
