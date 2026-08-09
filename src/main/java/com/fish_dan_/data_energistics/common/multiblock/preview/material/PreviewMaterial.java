package com.fish_dan_.data_energistics.common.multiblock.preview.material;

import appeng.api.stacks.AEItemKey;

/**
 * Component-aware immutable material amount used by previews and ordinary recipe views.
 *
 * @param key    item and data-component identity
 * @param amount exact positive required count
 */
public record PreviewMaterial(AEItemKey key, long amount) {

    /**
     * Rejects empty identities and non-positive amounts before recipe conversion.
     */
    public PreviewMaterial {
        if (key == null) {
            throw new IllegalArgumentException("Preview material requires an item key");
        }
        if (amount <= 0L) {
            throw new IllegalArgumentException("Preview material amount must be positive: " + amount);
        }
    }
}
