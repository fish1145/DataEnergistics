package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.applicability;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Exact acyclic residual needed alongside repeated firings of one primitive productive basis.
 *
 * @param firings        residual logical firing vector
 * @param netChange      residual net change
 * @param executionOrder topological residual order
 */
public record TrinityDeterministicResidualResult(
                                                 Map<TrinityPatternVariant, BigInteger> firings,
                                                 Map<AEKey, BigInteger> netChange,
                                                 List<TrinityVariantFiring> executionOrder) {}
