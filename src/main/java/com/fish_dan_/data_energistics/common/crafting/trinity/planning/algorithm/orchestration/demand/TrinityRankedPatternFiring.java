package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.demand;

import java.math.BigInteger;

/**
 * Retains one aggregated acyclic firing count together with its stable condensation execution rank.
 *
 * @param count exact positive logical firing count
 * @param rank  stable execution rank relative to cycle blocks
 */
public record TrinityRankedPatternFiring(BigInteger count, int rank) {}
