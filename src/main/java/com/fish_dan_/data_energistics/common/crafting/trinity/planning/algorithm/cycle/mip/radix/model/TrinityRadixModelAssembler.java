package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.model;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.bounds.TrinityCycleObjectiveBounds;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.model.TrinityCycleFeasibilityRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.mip.radix.codec.TrinityRadixCodec;

import java.math.BigInteger;

/**
 * Assembles one bounded radix feasibility model from immutable logical inputs and one objective pass.
 */
public interface TrinityRadixModelAssembler {

    /**
     * Creates an assembler sharing the exact codec and bound derivation used by objective probing.
     */
    static TrinityRadixModelAssembler create(
                                             TrinityRadixCodec codec,
                                             TrinityCycleObjectiveBounds exactBounds) {
        return new TrinityRadixModelAssemblerImpl(codec, exactBounds);
    }

    /**
     * Builds all logical variables, conservation rows, carry columns, and certified objective limits.
     *
     * @param request      immutable SCC feasibility request
     * @param pass         current sequential lexicographic pass
     * @param logicalUpper upper bound for every logical axis in this representability domain
     */
    TrinityRadixBuiltModel assemble(
                                    TrinityCycleFeasibilityRequest request,
                                    TrinityRadixModelPass pass,
                                    BigInteger logicalUpper);
}
