package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.template.TrinityMipCoefficientTemplate;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.proof.TrinityCycleUnitProofIndex;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.proof.TrinityAcyclicRouteFamily;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.util.List;
import java.util.Map;

/**
 * Immutable revision-independent structure reused while quantities and relevant inventory change.
 *
 * @param target                requested graph target
 * @param patternIdentities     complete ordered semantics of the target-reachable patterns
 * @param expandedVariantCount  total binding variants before strict transition-effect compaction
 * @param variants              immutable expanded binding variants
 * @param topology              immutable SCC partition and condensation topology
 * @param targetComponent       component containing the requested target
 * @param reachableCycle        whether an upstream component reachable from the target is cyclic
 * @param relevantInventoryKeys exact deterministic inventory projection read by demand solving
 * @param routeFamilies         quantity-independent DAG producer families
 * @param cycleUnitProofs       deduplicated deterministic units and productive-output aliases
 * @param cycleMipTemplates     sparse coefficient templates keyed by cyclic component index
 */
public record TrinityCompiledGraph(
                                   AEKey target,
                                   List<TrinityPatternIdentity> patternIdentities,
                                   int expandedVariantCount,
                                   List<TrinityPatternVariant> variants,
                                   TrinityCraftingTopology topology,
                                   int targetComponent,
                                   boolean reachableCycle,
                                   List<AEKey> relevantInventoryKeys,
                                   Map<AEKey, TrinityAcyclicRouteFamily> routeFamilies,
                                   TrinityCycleUnitProofIndex cycleUnitProofs,
                                   Int2ObjectMap<TrinityMipCoefficientTemplate> cycleMipTemplates) {

    /**
     * Attaches semantic proofs assembled through the shared per-Grid cache without changing graph topology.
     */
    public TrinityCompiledGraph withStructuralProofs(
                                                     Map<AEKey, TrinityAcyclicRouteFamily> newRouteFamilies,
                                                     TrinityCycleUnitProofIndex newCycleUnitProofs,
                                                     Int2ObjectMap<TrinityMipCoefficientTemplate> newCycleMipTemplates) {
        return new TrinityCompiledGraph(
                target,
                patternIdentities,
                expandedVariantCount,
                variants,
                topology,
                targetComponent,
                reachableCycle,
                relevantInventoryKeys,
                newRouteFamilies,
                newCycleUnitProofs,
                newCycleMipTemplates);
    }
}
