package com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan;

import appeng.api.stacks.AEKey;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Compact, prefix-validated repetition of an ordered cyclic stage sequence.
 *
 * @param index       stable repeat-block index
 * @param stageOrder  exact safe stage order for one cycle
 * @param repetitions complete-cycle count
 * @param minimumSeed maximum prefix deficit per key
 * @param netChange   signed balance change of the full repeated block
 */
public record TrinityCycleRepeatBlock(
                                      int index,
                                      List<Integer> stageOrder,
                                      BigInteger repetitions,
                                      Map<AEKey, BigInteger> minimumSeed,
                                      Map<AEKey, BigInteger> netChange) {

    /**
     * Ensures one compact block cannot contain an invalid or repeated stage reference.
     */
    public TrinityCycleRepeatBlock {
        if (index < 0 || stageOrder.isEmpty() || repetitions.signum() <= 0) {
            throw new IllegalArgumentException("A Trinity repeat block requires index, stages and repetitions");
        }
        stageOrder = Collections.unmodifiableList(stageOrder);
        IntSet seenStages = new IntOpenHashSet();
        for (int stage : stageOrder) {
            if (stage < 0 || !seenStages.add(stage)) {
                throw new IllegalArgumentException("A Trinity repeat block requires unique valid stage indexes");
            }
        }
        minimumSeed = TrinityPlanAmounts.validatePositive(minimumSeed, "repeat minimum seed");
        netChange = TrinityPlanAmounts.validateSignedNonZero(netChange, "repeat net change");
    }
}
