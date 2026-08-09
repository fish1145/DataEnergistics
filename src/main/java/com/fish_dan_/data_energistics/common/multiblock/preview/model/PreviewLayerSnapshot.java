package com.fish_dan_.data_energistics.common.multiblock.preview.model;

import com.modularmc.mdl.api.multiblock.PatternLayerSource;

import java.util.List;

/**
 * One immutable logical layer in an expanded preview.
 *
 * @param index  zero-based expanded layer index
 * @param source MDLib unit, repetition, and source-layer identity
 * @param cells  cells in stable row-major order
 */
public record PreviewLayerSnapshot(int index,
                                   PatternLayerSource source,
                                   List<PreviewCellSnapshot> cells) {

    /**
     * Copies cells and verifies that every cell belongs to this source layer.
     */
    public PreviewLayerSnapshot {
        if (index < 0 || cells.isEmpty()) {
            throw new IllegalArgumentException("Invalid preview layer snapshot");
        }
        cells = List.copyOf(cells);
        for (PreviewCellSnapshot cell : cells) {
            if (cell.source().unitIndex() != source.unitIndex() ||
                    cell.source().repeatIndex() != source.repeatIndex() ||
                    cell.source().sourceLayer() != source.sourceLayer()) {
                throw new IllegalArgumentException("Preview layer contains a cell from another MDLib source layer");
            }
        }
    }
}
