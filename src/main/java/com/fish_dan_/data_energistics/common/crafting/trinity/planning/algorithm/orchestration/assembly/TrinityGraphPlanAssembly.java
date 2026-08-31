package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.assembly;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCycleRepeatBlock;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanQuality;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * Complete plan payload after graph demand has been converted into deterministic execution structures.
 *
 * @param initialInputs        initial network inputs
 * @param patternFirings       aggregate firing counts by stable publication identity
 * @param stages               executable stage definitions
 * @param stageOrder           stable stage order
 * @param repeatBlocks         compressed cycle repeat blocks
 * @param minimumSeed          maximum seed reserve across cycle blocks
 * @param netChange            exact aggregate net change
 * @param stackRequests        exact byte-estimation stack volume
 * @param scheduleStates       total bounded search states
 * @param mipNanos             cycle MIP duration
 * @param quality              exact proof strength retained by the complete assembly
 * @param retainedSeed         internal restart balance required after every downstream stage
 * @param retainedSeedFinal    exact final balances observed on retained seed keys
 * @param seedRefinementPasses additional cycle solves used to prove terminal restart safety
 */
public record TrinityGraphPlanAssembly(
                                       Map<AEKey, BigInteger> initialInputs,
                                       Map<TrinityPatternIdentity, BigInteger> patternFirings,
                                       List<TrinityPlanStage> stages,
                                       List<Integer> stageOrder,
                                       List<TrinityCycleRepeatBlock> repeatBlocks,
                                       Map<AEKey, BigInteger> minimumSeed,
                                       Map<AEKey, BigInteger> netChange,
                                       Map<AEKey, BigInteger> stackRequests,
                                       int scheduleStates,
                                       long mipNanos,
                                       TrinityPlanQuality quality,
                                       Map<AEKey, BigInteger> retainedSeed,
                                       Map<AEKey, BigInteger> retainedSeedFinal,
                                       int seedRefinementPasses) {

    /**
     * Compatibility constructor for exact assembly paths.
     */
    public TrinityGraphPlanAssembly(
                                    Map<AEKey, BigInteger> initialInputs,
                                    Map<TrinityPatternIdentity, BigInteger> patternFirings,
                                    List<TrinityPlanStage> stages,
                                    List<Integer> stageOrder,
                                    List<TrinityCycleRepeatBlock> repeatBlocks,
                                    Map<AEKey, BigInteger> minimumSeed,
                                    Map<AEKey, BigInteger> netChange,
                                    Map<AEKey, BigInteger> stackRequests,
                                    int scheduleStates,
                                    long mipNanos) {
        this(
                initialInputs,
                patternFirings,
                stages,
                stageOrder,
                repeatBlocks,
                minimumSeed,
                netChange,
                stackRequests,
                scheduleStates,
                mipNanos,
                TrinityPlanQuality.PROVED_OPTIMAL,
                Map.of(),
                Map.of(),
                0);
    }
}
