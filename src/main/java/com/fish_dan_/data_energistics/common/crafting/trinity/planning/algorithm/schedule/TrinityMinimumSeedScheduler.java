package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/**
 * Finds the minimum exact internal seed for a fixed MIP firing vector using bounded compressed scheduling.
 */
public interface TrinityMinimumSeedScheduler {

    /**
     * @return stateless bounded scheduler
     */
    static TrinityMinimumSeedScheduler create() {
        return new TrinityMinimumSeedSchedulerImpl();
    }

    /**
     * @param firings       positive MIP firing vector
     * @param externalKeys  boundary keys charged by the first lexicographic objective
     * @param seedableKeys  internal SCC keys charged by the second lexicographic objective
     * @param minimumInputs conservation-required balances injected before schedule search
     * @param maximumInputs stock or upstream-production upper bound for each injected key
     * @param maxStates     compressed state limit
     * @param control       cancellation and deadline boundary
     * @return minimum seed and executable schedule, or stable rejection
     */
    TrinityAlgorithmResult<TrinityMinimumSeedSchedule> find(
                                                            Map<TrinityPatternVariant, BigInteger> firings,
                                                            Set<AEKey> externalKeys,
                                                            Set<AEKey> seedableKeys,
                                                            Map<AEKey, BigInteger> minimumInputs,
                                                            Map<AEKey, BigInteger> maximumInputs,
                                                            int maxStates,
                                                            TrinityPlanningControl control);
}
