package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import java.util.List;

/**
 * Deterministically materializes legal Cartesian input bindings from an immutable crafting graph.
 */
public interface TrinityPatternVariantExpander {

    /**
     * @return stateless exact expander
     */
    static TrinityPatternVariantExpander create() {
        return new TrinityPatternVariantExpanderImpl();
    }

    /**
     * @param snapshot    immutable graph revision
     * @param maxVariants hard cap for one Cartesian product and for additional binding branches across the graph;
     *                    every pattern's canonical first binding belongs to the base graph and does not consume this
     *                    budget
     * @return complete identity-ordered variants or {@code VARIANT_LIMIT}
     */
    TrinityAlgorithmResult<List<TrinityPatternVariant>> expand(
                                                               TrinityCraftingGraphSnapshot snapshot,
                                                               int maxVariants);
}
