package com.fish_dan_.data_energistics.common.multiblock.preview.model;

import appeng.api.stacks.AEItemKey;

import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Immutable pairing of one render state and the component-aware item that places it.
 *
 * @param state        exact state rendered by a preview scene
 * @param placementKey item consumed when this candidate is selected
 */
public record PreviewCandidate(Optional<BlockState> state, Optional<AEItemKey> placementKey) {

    /**
     * Ensures a candidate is either a complete state/item pair or an explicit empty choice.
     */
    public PreviewCandidate {
        if (state.isPresent() != placementKey.isPresent()) {
            throw new IllegalArgumentException("Preview candidate state and placement item must be present together");
        }
    }

    /**
     * Creates one concrete render/material choice.
     *
     * @param state        exact render state
     * @param placementKey immutable placement item identity
     * @return concrete candidate
     */
    public static PreviewCandidate concrete(BlockState state, AEItemKey placementKey) {
        return new PreviewCandidate(Optional.of(state), Optional.of(placementKey));
    }

    /**
     * Creates the explicit no-block choice used by air and optional predicates.
     *
     * @return empty candidate
     */
    public static PreviewCandidate empty() {
        return new PreviewCandidate(Optional.empty(), Optional.empty());
    }

    /**
     * Returns whether this choice renders and consumes a concrete placement item.
     */
    public boolean concrete() {
        return this.state.isPresent();
    }
}
