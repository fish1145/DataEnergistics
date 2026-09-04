package com.fish_dan_.data_energistics.api.registry.machine.capacity;

/** Common-setup declaration facet for external crafting-machine remaining capacity. */
public interface CraftingMachineCapacityRegistry {

    /**
     * Registers one immutable machine-capacity declaration.
     *
     * @param registration declaration to stage in the current plugin transaction
     */
    void register(CraftingMachineCapacityRegistration registration);
}
