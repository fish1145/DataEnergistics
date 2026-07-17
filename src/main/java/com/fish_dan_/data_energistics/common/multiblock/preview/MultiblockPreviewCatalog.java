package com.fish_dan_.data_energistics.common.multiblock.preview;

/**
 * Supplies controller preview specs without exposing registry locking or reload internals to consumers.
 */
public interface MultiblockPreviewCatalog {

    /**
     * Builds an immutable catalog from one current definition registry generation.
     */
    MultiblockPreviewCatalogSnapshot snapshot();
}
