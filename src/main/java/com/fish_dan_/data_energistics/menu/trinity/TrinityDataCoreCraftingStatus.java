package com.fish_dan_.data_energistics.menu.trinity;

import appeng.api.stacks.GenericStack;
import org.jspecify.annotations.Nullable;

/**
 * Snapshot of the crafting CPU work visible to the Trinity Data Core GUI.
 */
public record TrinityDataCoreCraftingStatus(@Nullable GenericStack target,
                                            int busyCpuCount,
                                            int cpuPartitionCount,
                                            int busyCpuPartitionCount,
                                            long cpuStorageBytes,
                                            int cpuCoProcessors) {

    public static final TrinityDataCoreCraftingStatus EMPTY = new TrinityDataCoreCraftingStatus(null, 0, 0, 0, 0L, 0);

    public boolean hasTarget() {
        return this.target != null && this.target.amount() > 0;
    }
}
