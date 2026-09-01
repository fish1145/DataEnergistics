package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;

/**
 * Exact AE2-compatible storage-byte inputs accumulated by a compact graph planner.
 *
 * @param stackRequestAmounts amount requested at every logical stack node, already including slot multipliers
 * @param patternFirings      total logical pattern firing count
 * @param logicalNodeCount    compact crafting-tree node count
 */
public record TrinityPlanByteEstimateInput(
                                           Map<AEKey, BigInteger> stackRequestAmounts,
                                           BigInteger patternFirings,
                                           BigInteger logicalNodeCount) {

    /**
     * Validates the owned totals and rejects incomplete byte-accounting inputs.
     */
    public TrinityPlanByteEstimateInput {
        stackRequestAmounts = TrinityPlanAmounts.validatePositive(stackRequestAmounts, "byte stack request");
        if (patternFirings.signum() < 0 || logicalNodeCount.signum() < 0) {
            throw new IllegalArgumentException("Trinity byte estimate counters must be non-negative");
        }
    }
}
