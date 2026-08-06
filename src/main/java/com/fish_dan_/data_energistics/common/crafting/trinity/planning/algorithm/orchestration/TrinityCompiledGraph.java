package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.util.HashSet;
import java.util.List;

/**
 * Immutable revision-independent structure reused while quantities and relevant inventory change.
 *
 * @param target                requested graph target
 * @param patternIdentities     complete ordered semantics of the target-reachable patterns
 * @param variants              immutable expanded binding variants
 * @param topology              immutable SCC partition and condensation topology
 * @param targetComponent       component containing the requested target
 * @param reachableCycle        whether an upstream component reachable from the target is cyclic
 * @param relevantInventoryKeys exact deterministic inventory projection read by demand solving
 */
public record TrinityCompiledGraph(
                                   AEKey target,
                                   List<TrinityPatternIdentity> patternIdentities,
                                   List<TrinityPatternVariant> variants,
                                   TrinityCraftingTopology topology,
                                   int targetComponent,
                                   boolean reachableCycle,
                                   List<AEKey> relevantInventoryKeys) {

    /**
     * Copies every pure value and verifies target and inventory-key coverage.
     */
    public TrinityCompiledGraph {
        if (target == null || patternIdentities == null || patternIdentities.isEmpty() || variants == null ||
                variants.isEmpty() || topology == null || targetComponent < 0 ||
                targetComponent >= topology.components().size() || relevantInventoryKeys == null ||
                !Integer.valueOf(targetComponent).equals(topology.componentByKey().get(target))) {
            throw new IllegalArgumentException("A Trinity compiled graph requires complete target structure");
        }
        patternIdentities = List.copyOf(patternIdentities);
        variants = List.copyOf(variants);
        relevantInventoryKeys = List.copyOf(relevantInventoryKeys);
        if (new HashSet<>(patternIdentities).size() != patternIdentities.size() ||
                new HashSet<>(relevantInventoryKeys).size() != relevantInventoryKeys.size() ||
                !relevantInventoryKeys.contains(target) ||
                !topology.componentByKey().keySet().equals(new HashSet<>(relevantInventoryKeys))) {
            throw new IllegalArgumentException("A Trinity compiled graph requires unique complete semantics and inventory keys");
        }
    }
}
