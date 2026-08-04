package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.search;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixCodec;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixResultDecoder;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model.TrinityRadixBuiltModel;

import org.ojalgo.optimisation.Variable;

import java.math.BigInteger;
import java.util.Map;

/**
 * Proves one radix objective exactly using a certified full-value probe followed by bounded per-digit feasibility.
 */
public interface TrinityRadixObjectiveSearch {

    /**
     * Creates an objective search sharing the encoder's radix codec and exact result decoder.
     */
    static TrinityRadixObjectiveSearch create(
                                              TrinityRadixCodec codec,
                                              TrinityRadixResultDecoder resultDecoder) {
        return new TrinityRadixObjectiveSearchImpl(codec, resultDecoder);
    }

    /**
     * Selects the exact lexicographic optimum for the assembled objective.
     */
    TrinityAlgorithmResult<Map<Variable, BigInteger>> optimize(
                                                               TrinityRadixBuiltModel built,
                                                               TrinityPlanningControl control,
                                                               TrinityRadixSolverMetrics metrics);

    /**
     * Finds any exactly decoded witness in a proof-domain model used only to distinguish overflow from infeasibility.
     */
    TrinityAlgorithmResult<Map<Variable, BigInteger>> findFeasible(
                                                                   TrinityRadixBuiltModel built,
                                                                   TrinityPlanningControl control,
                                                                   TrinityRadixSolverMetrics metrics);
}
