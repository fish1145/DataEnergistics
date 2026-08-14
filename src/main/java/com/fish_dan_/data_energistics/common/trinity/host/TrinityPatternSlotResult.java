package com.fish_dan_.data_energistics.common.trinity.host;

import net.minecraft.world.item.ItemStack;

/**
 * Authoritative slot-action outcome and resulting carried stack.
 */
public record TrinityPatternSlotResult(TrinityHostedActionStatus status, ItemStack carried) {

    public TrinityPatternSlotResult {
        carried = carried.copy();
    }
}
