package com.fish_dan_.data_energistics.common.multiblock.preview.material;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import net.minecraft.world.item.ItemStack;

/**
 * Component-aware immutable material amount used by previews and ordinary recipe views.
 *
 * @param key    item or fluid identity including data components
 * @param amount exact positive count, in items or mB according to the key type
 */
public record PreviewMaterial(AEKey key, long amount) {

    /** Converts material at the UI boundary, retaining fluid identity and quantity in AE2's display wrapper. */
    public ItemStack displayStack(long amount) {
        return key instanceof AEItemKey item ? item.toStack(Math.toIntExact(amount)) : GenericStack.wrapInItemStack(key, amount);
    }

    /**
     * Rejects empty identities and non-positive amounts before recipe conversion.
     */
    public PreviewMaterial {
        if (key == null) {
            throw new IllegalArgumentException("Preview material requires an AE key");
        }
        if (amount <= 0L) {
            throw new IllegalArgumentException("Preview material amount must be positive: " + amount);
        }
    }
}
