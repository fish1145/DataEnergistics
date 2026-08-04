package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.cut;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint.search.TrinityFiringBox;

import java.util.List;
import java.util.Optional;

/**
 * Exact disjoint split induced by transitions that cannot start within an optimistic external-input cap.
 *
 * @param withinCap box with every unreachable transition fixed to zero, when non-empty
 * @param aboveCap  disjoint complement boxes, each forcing one unreachable transition to fire
 */
public record TrinityExternalPrefixPartition(
                                             Optional<TrinityFiringBox> withinCap,
                                             List<TrinityFiringBox> aboveCap) {

    /**
     * Freezes one effective partition.
     */
    public TrinityExternalPrefixPartition {
        if (withinCap == null || aboveCap == null || aboveCap.stream().anyMatch(box -> box == null)) {
            throw new IllegalArgumentException("A Trinity external-prefix partition must be complete");
        }
        aboveCap = List.copyOf(aboveCap);
        if (withinCap.isEmpty() && aboveCap.isEmpty()) {
            throw new IllegalArgumentException("A Trinity external-prefix partition cannot be empty");
        }
    }
}
