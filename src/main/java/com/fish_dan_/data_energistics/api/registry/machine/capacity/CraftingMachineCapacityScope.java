package com.fish_dan_.data_energistics.api.registry.machine.capacity;

/**
 * Capacity-pool identity retained through 3.2.x.
 *
 * @deprecated New machine capabilities use {@code CraftingMachineScope}; capacity registrations keep this return
 *             type until 3.3.0 for binary compatibility.
 */
@Deprecated(since = "3.2.0")
public enum CraftingMachineCapacityScope {
    /** Every provider and input face reaching the same block entity consumes one shared capacity pool. */
    BLOCK_ENTITY,
    /** Each input face of the same block entity has an independent capacity pool. */
    INPUT_SIDE
}
