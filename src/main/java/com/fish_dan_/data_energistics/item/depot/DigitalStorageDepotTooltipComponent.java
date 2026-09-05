package com.fish_dan_.data_energistics.item.depot;

import appeng.api.stacks.GenericStack;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.List;

public record DigitalStorageDepotTooltipComponent(
                                                  List<GenericStack> items,
                                                  List<GenericStack> fluids,
                                                  List<GenericStack> keys)
        implements TooltipComponent {

    public boolean isEmpty() {
        return this.items.isEmpty() && this.fluids.isEmpty() && this.keys.isEmpty();
    }
}
