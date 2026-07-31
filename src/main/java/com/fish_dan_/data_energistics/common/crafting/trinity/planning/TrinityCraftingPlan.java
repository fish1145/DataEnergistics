package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Executable compact plan understood exclusively by Trinity crafting CPUs.
 */
public interface TrinityCraftingPlan extends ICraftingPlan {

    /**
     * @return crafting-provider revision from which every retained pattern signature was captured
     */
    long catalogRevision();

    /**
     * @return requested net-new or final-total delivery semantics
     */
    CraftingQuantityMode quantityMode();

    /**
     * @return exact initial material estimate, including required seed
     */
    Map<AEKey, BigInteger> initialExpectedInputs();

    /**
     * @return aggregate logical firing count keyed by stable published pattern identity
     */
    Map<TrinityPatternIdentity, BigInteger> patternFirings();

    /**
     * @return immutable dependency-addressable stages
     */
    List<TrinityPlanStage> stages();

    /**
     * @return deterministic topological stage order
     */
    List<Integer> stageOrder();

    /**
     * @return compact cyclic repeat blocks
     */
    List<TrinityCycleRepeatBlock> cycleRepeatBlocks();

    /**
     * @return exact maximum prefix deficit reserved before execution
     */
    Map<AEKey, BigInteger> minimumSeed();

    /**
     * @return exact signed net change after all stages and repetitions
     */
    Map<AEKey, BigInteger> targetNetChange();

    /**
     * @return informational diagnostics retained with a successful plan
     */
    List<TrinityPlanningDiagnostic> diagnostics();

    /**
     * @return deterministic solver and scheduler counters
     */
    TrinityPlanningStatistics statistics();
}
