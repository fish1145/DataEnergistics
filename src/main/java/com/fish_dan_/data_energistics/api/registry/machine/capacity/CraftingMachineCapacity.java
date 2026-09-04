package com.fish_dan_.data_energistics.api.registry.machine.capacity;

/**
 * Proven remaining logical capacity of one physical crafting machine.
 *
 * @param remainingLogicalCrafts non-negative number of complete additional crafts the machine can currently accept
 */
public record CraftingMachineCapacity(long remainingLogicalCrafts) {

    public CraftingMachineCapacity {
        if (remainingLogicalCrafts < 0L) {
            throw new IllegalArgumentException("Crafting machine remaining capacity must not be negative");
        }
    }
}
