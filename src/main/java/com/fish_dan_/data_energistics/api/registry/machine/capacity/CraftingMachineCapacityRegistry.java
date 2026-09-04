package com.fish_dan_.data_energistics.api.registry.machine.capacity;

/**
 * Legacy capacity-only registration facet retained through 3.2.x.
 *
 * @deprecated Use {@code DataEnergisticsRegistry.craftingMachines()} and
 *             {@code CraftingMachineRegistry.registerCapacity}; this alias is removed in 3.3.0.
 */
@Deprecated(since = "3.2.0")
public interface CraftingMachineCapacityRegistry {

    /**
     * Registers one immutable machine-capacity declaration.
     *
     * @param registration declaration to stage in the current plugin transaction
     */
    void register(CraftingMachineCapacityRegistration registration);
}
