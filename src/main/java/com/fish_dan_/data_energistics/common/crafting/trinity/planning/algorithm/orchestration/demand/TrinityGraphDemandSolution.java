package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.demand;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.selection.TrinityCycleSelection;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable output of reverse graph-demand aggregation before execution stages are assembled.
 *
 * @param initialInputs  inventory reserved by the aggregate demand search
 * @param acyclicFirings aggregated non-cycle firings and their execution ranks
 * @param cycleSolutions selected executable cycle plans
 * @param scheduleStates combined cycle and route-search states
 * @param mipNanos       MIP time contributed by selected cycle plans
 */
public record TrinityGraphDemandSolution(
                                         Map<AEKey, BigInteger> initialInputs,
                                         Map<TrinityPatternVariant, TrinityRankedPatternFiring> acyclicFirings,
                                         List<TrinityCycleSelection> cycleSolutions,
                                         int scheduleStates,
                                         long mipNanos) {

    public TrinityGraphDemandSolution {
        initialInputs = Collections.unmodifiableMap(new LinkedHashMap<>(initialInputs));
        acyclicFirings = Collections.unmodifiableMap(new LinkedHashMap<>(acyclicFirings));
        cycleSolutions = List.copyOf(cycleSolutions);
    }

    /**
     * @return weakest proof quality among every selected cycle
     */
    public TrinityPlanQuality quality() {
        TrinityPlanQuality quality = TrinityPlanQuality.PROVED_OPTIMAL;
        for (TrinityCycleSelection cycle : this.cycleSolutions) {
            quality = quality.combine(cycle.quality());
        }
        return quality;
    }
}
