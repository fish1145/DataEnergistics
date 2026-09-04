package com.fish_dan_.data_energistics.api.registry.machine.capacity;

import java.util.Optional;

/**
 * Read-only adapter for machine queue, parallel-slot or other capacity that inventory insertion cannot represent.
 *
 * <p>
 * The callback runs on the server thread during capacity capture and again immediately before dispatch preparation.
 * It must not reserve work, consume inputs, mutate the machine or retain the supplied context. An empty result means
 * that this registered machine type does not apply to the current pattern and lets ordinary insertion simulation
 * decide the capacity. A present zero is authoritative exhaustion and prevents fallback dispatch.
 * </p>
 */
@FunctionalInterface
public interface CraftingMachineCapacityAdapter {

    /**
     * Resolves the current safe remaining logical capacity for one exact machine and pattern.
     *
     * @param context ephemeral live machine and pattern context
     * @return current capacity, or empty when this adapter does not apply to the pattern
     */
    Optional<CraftingMachineCapacity> capture(CraftingMachineCapacityContext context);
}
