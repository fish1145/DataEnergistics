package com.fish_dan_.data_energistics.common.multiblock.preview.model;

import net.minecraft.core.BlockPos;

import com.modularmc.mdl.api.multiblock.PatternCellSource;

/**
 * One immutable projected cell with source metadata and resolved candidate choices.
 *
 * @param relativePosition controller-relative canonical preview position
 * @param source           unexpanded MDLib source and repetition metadata
 * @param predicate        immutable predicate candidate snapshot
 */
public record PreviewCellSnapshot(BlockPos relativePosition,
                                  PatternCellSource source,
                                  PreviewPredicateSnapshot predicate) {

    /**
     * Detaches the position and verifies that predicate identity matches the source coordinate.
     */
    public PreviewCellSnapshot {
        relativePosition = relativePosition.immutable();
        PreviewPredicateKey expectedKey = new PreviewPredicateKey(source.sourceLayer(), source.y(), source.x());
        if (!expectedKey.equals(predicate.key())) {
            throw new IllegalArgumentException("Preview cell predicate key does not match its MDLib source");
        }
    }
}
