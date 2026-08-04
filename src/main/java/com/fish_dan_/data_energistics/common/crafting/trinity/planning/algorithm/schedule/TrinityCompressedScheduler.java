package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;

/**
 * Validates a firing vector using maximum-safe batches and relevant balance breakpoints.
 */
public interface TrinityCompressedScheduler {

    /**
     * @return stateless exact scheduler
     */
    static TrinityCompressedScheduler create() {
        return new TrinityCompressedSchedulerImpl();
    }

    /**
     * @param firings         complete positive firing vector
     * @param initialBalances exact seed and external inputs owned before the first firing
     * @param maxStates       positive compressed search-state limit
     * @param control         cancellation and deadline boundary
     * @return executable compressed order or stable rejection
     */
    TrinityAlgorithmResult<TrinityCompressedSchedule> schedule(
                                                               Map<TrinityPatternVariant, BigInteger> firings,
                                                               Map<AEKey, BigInteger> initialBalances,
                                                               int maxStates,
                                                               TrinityPlanningControl control);
}
