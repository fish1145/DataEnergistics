package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.proof;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.deterministic.firing.TrinityDeterministicFiringSolution;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityDeterministicRepeatScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityMinimumSeedScheduler;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

/**
 * Converts an exact deterministic firing vector into an executable seed, prefix, repeat, and suffix proof.
 */
public interface TrinityDeterministicProofAssembler {

    /**
     * Creates the proof assembler from exact minimum-seed and repeated-cycle schedulers.
     */
    static TrinityDeterministicProofAssembler create(
                                                     TrinityMinimumSeedScheduler seedScheduler,
                                                     TrinityDeterministicRepeatScheduler repeatScheduler) {
        return new TrinityDeterministicProofAssemblerImpl(seedScheduler, repeatScheduler);
    }

    /**
     * Proves inventory bounds, exact minimum seed, compressed executability, and the full objective tuple.
     */
    TrinityAlgorithmResult<TrinityDeterministicCandidate> assemble(
                                                                   TrinityStronglyConnectedComponent component,
                                                                   TrinityCycleDemand demand,
                                                                   Map<AEKey, BigInteger> available,
                                                                   Set<AEKey> producibleInputs,
                                                                   TrinityDeterministicFiringSolution firingSolution,
                                                                   int maxStates,
                                                                   TrinityPlanningControl control);
}
