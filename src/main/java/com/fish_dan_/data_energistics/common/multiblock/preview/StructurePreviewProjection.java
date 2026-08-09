package com.fish_dan_.data_energistics.common.multiblock.preview;

import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockPreviewSpec;

/**
 * Builds common-layer preview snapshots from revision-bound definitions and session choices.
 */
public interface StructurePreviewProjection {

    /**
     * Expands and resolves the currently active substructure.
     *
     * @param spec      active preview catalog
     * @param selection current revision-bound session selection
     * @return immutable full snapshot
     */
    StructurePreviewSnapshot project(MultiblockPreviewSpec spec, PreviewSelection selection);
}
