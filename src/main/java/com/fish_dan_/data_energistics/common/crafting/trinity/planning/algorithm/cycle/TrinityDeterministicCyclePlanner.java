package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityDeterministicRepeatScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Solves a stable deterministic cycle by closed-form net effect and maximum prefix deficit.
 */
public interface TrinityDeterministicCyclePlanner {

    /**
     * @return planner using the exact compressed scheduler
     */
    static TrinityDeterministicCyclePlanner create() {
        return new TrinityDeterministicCyclePlannerImpl(TrinityDeterministicRepeatScheduler.create());
    }

    /**
     * @param oneCycleOrder     ordered compact firings in one complete production cycle
     * @param target            requested productive key
     * @param requestedAmount   positive requested delivery
     * @param quantityMode      net-new or final-total semantics
     * @param available         non-negative immutable inventory snapshot
     * @param maxScheduleStates compressed scheduling state bound
     * @param control           cancellation and deadline boundary
     * @return exact compact cycle or stable rejection
     */
    TrinityAlgorithmResult<TrinityCyclePlan> plan(
                                                  List<TrinityVariantFiring> oneCycleOrder,
                                                  AEKey target,
                                                  BigInteger requestedAmount,
                                                  CraftingQuantityMode quantityMode,
                                                  Map<AEKey, BigInteger> available,
                                                  int maxScheduleStates,
                                                  TrinityPlanningControl control);
}
