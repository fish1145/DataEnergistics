package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.inventory.TrinityPlanningInventory;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.request.TrinityPlanningLimits;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;

/**
 * Immutable pure-algorithm request shared by initial planning and remaining-work replanning.
 *
 * @param gridScope       server-lifetime Grid publication scope
 * @param graph           immutable current graph revision
 * @param target          requested output
 * @param requestedAmount positive requested delivery
 * @param quantityMode    net-new or final-total semantics
 * @param inventory       exact finite/unlimited server-thread inventory snapshot
 * @param limits          immutable planner bounds
 */
public record TrinityPlanningInput(
                                   long gridScope,
                                   TrinityCraftingGraphSnapshot graph,
                                   AEKey target,
                                   BigInteger requestedAmount,
                                   CraftingQuantityMode quantityMode,
                                   TrinityPlanningInventory inventory,
                                   TrinityPlanningLimits limits) {}
