package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.proof;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;

import appeng.api.stacks.AEKey;

import java.util.List;

/**
 * Quantity-free identities selected by one previously verified local DAG route.
 *
 * @param output             exact competition output
 * @param selectedIdentities stable real pattern identities; firing counts remain request-local
 */
public record TrinityAcyclicRouteHint(AEKey output, List<TrinityPatternIdentity> selectedIdentities) {}
