package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;

import appeng.api.stacks.AEKey;

import java.util.List;

/**
 * One deterministic strongly connected key component and the transitions wholly contained by it.
 *
 * @param index              stable component index
 * @param keys               keys in original graph order
 * @param cyclic             whether the component contains a directed cycle
 * @param cycleVariants      variants inducing at least one input-to-output edge inside this component
 * @param predecessorIndexes input-side components in the condensation DAG
 * @param successorIndexes   output-side components in the condensation DAG
 */
public record TrinityStronglyConnectedComponent(
                                                int index,
                                                List<AEKey> keys,
                                                boolean cyclic,
                                                List<TrinityPatternVariant> cycleVariants,
                                                List<Integer> predecessorIndexes,
                                                List<Integer> successorIndexes) {

    /**
     * Copies the immutable topology surface and rejects empty or self-referential components.
     */
    public TrinityStronglyConnectedComponent {
        if (index < 0 || keys == null || keys.isEmpty() || cycleVariants == null ||
                predecessorIndexes == null || successorIndexes == null) {
            throw new IllegalArgumentException("A Trinity strongly connected component requires complete topology");
        }
        keys = List.copyOf(keys);
        cycleVariants = List.copyOf(cycleVariants);
        predecessorIndexes = copyComponentIndexes(predecessorIndexes, index);
        successorIndexes = copyComponentIndexes(successorIndexes, index);
    }

    private static List<Integer> copyComponentIndexes(List<Integer> source, int ownIndex) {
        List<Integer> copied = List.copyOf(source);
        int previous = -1;
        for (Integer index : copied) {
            if (index == null || index < 0 || index == ownIndex || index <= previous) {
                throw new IllegalArgumentException("Trinity condensation edges must be sorted, unique and external");
            }
            previous = index;
        }
        return copied;
    }
}
