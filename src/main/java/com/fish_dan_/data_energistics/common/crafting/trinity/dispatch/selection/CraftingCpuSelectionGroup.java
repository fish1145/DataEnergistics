package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.selection;

import appeng.api.config.CpuSelectionMode;

/**
 * Hardware-equivalent candidate group that owns an independent successful-submission round-robin cursor.
 *
 * @param selectionMode exact source-selection contract so player-only and machine-only cursors cannot overwrite each
 *                      other
 * @param preferred     whether the selection mode is preferred for the current source
 * @param coProcessors  complete co-processor count
 * @param storageBytes  complete job storage
 */
public record CraftingCpuSelectionGroup(CpuSelectionMode selectionMode,
                                        boolean preferred,
                                        int coProcessors,
                                        long storageBytes) {

    public CraftingCpuSelectionGroup {
        if (selectionMode == null) {
            throw new IllegalArgumentException("Crafting CPU selection group mode must not be null");
        }
        if (coProcessors < 0) {
            throw new IllegalArgumentException("Crafting CPU selection group co-processors must not be negative");
        }
        if (storageBytes < 0L) {
            throw new IllegalArgumentException("Crafting CPU selection group storage must not be negative");
        }
    }
}
