package com.fish_dan_.data_energistics.menu.patternencoding;

import com.fish_dan_.data_energistics.common.multiblock.preview.catalog.MultiblockRecipeView;

import appeng.parts.encoding.EncodingMode;
import appeng.util.ConfigInventory;

/**
 * Typed access to the real AE2 pattern-encoding state used by authoritative multiblock recipe transfer.
 */
public interface PatternEncodingMultiblockTransferTarget {

    /**
     * Sends the current typed recipe identity from a client XEI integration to the exact open menu.
     */
    void data_energistics$requestMultiblockTransfer(MultiblockRecipeView recipe);

    /**
     * Returns the live input configuration inventory whose actual capacity constrains transfer.
     */
    ConfigInventory data_energistics$getMultiblockTransferInputInventory();

    /**
     * Returns the live output configuration inventory whose actual capacity constrains transfer.
     */
    ConfigInventory data_energistics$getMultiblockTransferOutputInventory();

    /**
     * Returns the encoding mode that must be restored if an atomic transfer fails.
     */
    EncodingMode data_energistics$getMultiblockTransferEncodingMode();

    /**
     * Changes the live encoding mode only after every transferred slot has been verified.
     */
    void data_energistics$setMultiblockTransferEncodingMode(EncodingMode mode);

    /**
     * Snapshots every source-specific field that an atomic transfer must restore on failure.
     */
    PatternEncodingMultiblockTransferState data_energistics$snapshotMultiblockTransferState();

    /**
     * Clears source, key, and fluid memory before the generic Processing configuration is published.
     */
    void data_energistics$clearMultiblockTransferState();

    /**
     * Restores a previously captured source-specific state during transaction rollback.
     */
    void data_energistics$restoreMultiblockTransferState(PatternEncodingMultiblockTransferState state);

    /**
     * Invalidates the menu after an incomplete rollback leaves its encoding state uncertain.
     */
    void data_energistics$invalidateMultiblockTransferTarget();
}
