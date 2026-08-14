package com.fish_dan_.data_energistics.api.crafting.dynamic;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

import java.util.Objects;

/**
 * One pattern-declared physical output that may be completed by a related runtime key.
 *
 * @param plannedOutput complete declared key and positive amount for one logical pattern push
 * @param matchMode     policy used only when an exact output-key match is unavailable
 */
public record DynamicCraftingOutput(GenericStack plannedOutput,
                                    DynamicCraftingOutputMatchMode matchMode) {

    /**
     * Rejects declarations that cannot be represented safely by the supported matching policies.
     */
    public DynamicCraftingOutput {
        Objects.requireNonNull(plannedOutput, "Planned dynamic crafting output must not be null");
        Objects.requireNonNull(plannedOutput.what(), "Planned dynamic crafting output key must not be null");
        Objects.requireNonNull(matchMode, "Dynamic crafting output match mode must not be null");
        if (plannedOutput.amount() <= 0L) {
            throw new IllegalArgumentException("Dynamic crafting output requires a positive planned amount");
        }
        if (matchMode == DynamicCraftingOutputMatchMode.SAME_ITEM && !(plannedOutput.what() instanceof AEItemKey)) {
            throw new IllegalArgumentException("SAME_ITEM dynamic crafting outputs require an AE item key");
        }
    }
}
