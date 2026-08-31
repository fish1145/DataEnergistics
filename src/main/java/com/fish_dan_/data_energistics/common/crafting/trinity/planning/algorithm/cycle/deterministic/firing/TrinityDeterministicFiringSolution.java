package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.firing;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability.TrinityDeterministicBasis;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;

/**
 * Exact firing vector and net-change proof derived from one applicable primitive basis.
 *
 * @param basis         structural basis used by proof assembly
 * @param firings       selected exact feasible firing vector
 * @param totalNet      exact aggregate net change
 * @param balancePasses bounded residual/repetition refinement passes
 */
public record TrinityDeterministicFiringSolution(
                                                 TrinityDeterministicBasis basis,
                                                 Map<TrinityPatternVariant, BigInteger> firings,
                                                 Map<AEKey, BigInteger> totalNet,
                                                 int balancePasses) {}
