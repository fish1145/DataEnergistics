package com.fish_dan_.data_energistics.api.registry.machine;

import com.fish_dan_.data_energistics.api.registry.machine.capacity.CraftingMachineCapacityRegistration;
import com.fish_dan_.data_energistics.api.registry.machine.upload.PatternUploadWorkstationRegistration;

/**
 * Common-setup declaration facet for external crafting-machine behavior.
 *
 * <p>
 * Capacity observations and pattern-upload transactions are independent optional capabilities. A machine may
 * register either one or both without being forced into a particular pattern-provider implementation.
 * </p>
 */
public interface CraftingMachineRegistry {

    /**
     * Registers one immutable remaining-capacity declaration.
     *
     * @param registration declaration to stage in the current plugin transaction
     */
    void registerCapacity(CraftingMachineCapacityRegistration registration);

    /**
     * Registers one immutable pattern-upload workstation declaration.
     *
     * @param registration declaration to stage in the current plugin transaction
     */
    void registerPatternUploadWorkstation(PatternUploadWorkstationRegistration registration);
}
