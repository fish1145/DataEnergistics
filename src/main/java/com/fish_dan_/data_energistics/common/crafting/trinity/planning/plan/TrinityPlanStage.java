package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import it.unimi.dsi.fastutil.ints.IntSortedSets;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * Validates the owned execution surface and rejects ambiguous duplicate firing entries.
     */
    public TrinityPlanStage {
        if (index < 0 || firings.isEmpty()) {
            throw new IllegalArgumentException("A Trinity stage requires an index, dependencies and firings");
        }
        IntSortedSet sortedDependencies = new IntAVLTreeSet();
        for (int dependency : dependencies) {
            if (dependency < 0 || dependency == index) {
                throw new IllegalArgumentException("A Trinity stage dependency must reference another stage");
            }
            sortedDependencies.add(dependency);
        }
        dependencies = IntSortedSets.unmodifiable(sortedDependencies);
        firings = Collections.unmodifiableList(firings);
        ObjectSet<String> bindings = new ObjectOpenHashSet<>();
        for (TrinityPlanPatternFiring firing : firings) {
            String binding = firing.patternIdentity().publicationEncoding() + '#' + firing.variantOrdinal();
            if (!bindings.add(binding)) {
                throw new IllegalArgumentException("A Trinity stage must aggregate duplicate pattern bindings");
            }
        }
        requiredAtStart = TrinityPlanAmounts.validatePositive(requiredAtStart, "stage start");
        netChange = TrinityPlanAmounts.validateSignedNonZero(netChange, "stage net change");
    }
}
