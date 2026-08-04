package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import java.util.List;

/**
 * Partitions the immutable AE key hypergraph with Tarjan and builds its condensation DAG.
 */
public interface TrinityGraphTopologyAnalyzer {

    /**
     * @return stateless deterministic analyzer
     */
    static TrinityGraphTopologyAnalyzer create() {
        return new TrinityGraphTopologyAnalyzerImpl();
    }

    /**
     * @param snapshot   graph key order and revision
     * @param variants   complete bound transition set for the snapshot
     * @param maxSccKeys configured per-component key limit
     * @return topology or {@code SCC_KEY_LIMIT}
     */
    TrinityAlgorithmResult<TrinityCraftingTopology> analyze(
                                                            TrinityCraftingGraphSnapshot snapshot,
                                                            List<TrinityPatternVariant> variants,
                                                            int maxSccKeys);
}
