package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.List;

/**
 * Immutable graph-level context needed to finalise one assembled plan without retaining mutable grid state.
 *
 * @param catalogRevision source graph revision
 * @param target          requested output
 * @param requestedAmount exact requested quantity
 * @param quantityMode    selected quantity semantics
 * @param variants        immutable expanded variants used for statistics and route flags
 * @param topology        immutable SCC topology used for statistics
 * @param startedNanos    observational planning start time
 */
public record TrinityGraphPlanContext(
                                      long catalogRevision,
                                      AEKey target,
                                      BigInteger requestedAmount,
                                      CraftingQuantityMode quantityMode,
                                      List<TrinityPatternVariant> variants,
                                      TrinityCraftingTopology topology,
                                      long startedNanos) {

    public TrinityGraphPlanContext {
        variants = List.copyOf(variants);
    }
}
