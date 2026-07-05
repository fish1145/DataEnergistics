package com.fish_dan_.data_energistics.menu;

import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;

/**
 * Snapshot of the crafting CPU work visible to the Digital Construct Flower GUI.
 */
public record DigitalConstructFlowerCraftingStatus(@Nullable GenericStack target, int busyCpuCount) {

    public static final DigitalConstructFlowerCraftingStatus EMPTY = new DigitalConstructFlowerCraftingStatus(null, 0);

    public boolean hasTarget() {
        return this.target != null && this.target.what() != null && this.target.amount() > 0;
    }
}
