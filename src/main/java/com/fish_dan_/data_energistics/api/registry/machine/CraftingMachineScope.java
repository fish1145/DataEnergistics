package com.fish_dan_.data_energistics.api.registry.machine;

/** Defines whether one machine capability is shared by a complete block entity or isolated per input face. */
public enum CraftingMachineScope {
    /** Every provider and input face reaching the same block entity shares one machine capability. */
    BLOCK_ENTITY,
    /** Each input face of the same block entity exposes an independent machine capability. */
    INPUT_SIDE
}
