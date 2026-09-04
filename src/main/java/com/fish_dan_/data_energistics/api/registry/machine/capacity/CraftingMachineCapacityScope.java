package com.fish_dan_.data_energistics.api.registry.machine.capacity;

/** Defines whether one registered capacity pool is shared by a complete block entity or isolated per input face. */
public enum CraftingMachineCapacityScope {
    /** Every provider and input face reaching the same block entity consumes one shared capacity pool. */
    BLOCK_ENTITY,
    /** Each input face of the same block entity has an independent capacity pool. */
    INPUT_SIDE
}
