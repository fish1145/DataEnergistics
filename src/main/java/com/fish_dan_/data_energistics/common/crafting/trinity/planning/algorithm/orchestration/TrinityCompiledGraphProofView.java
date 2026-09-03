package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag.proof.TrinityAcyclicRouteHint;

import appeng.api.stacks.AEKey;

import java.util.Map;

/** Request-local route hints layered over one already validated immutable target structure. */
public record TrinityCompiledGraphProofView(
                                            TrinityCompiledGraph structure,
                                            Map<AEKey, TrinityAcyclicRouteHint> routeHints) {}
