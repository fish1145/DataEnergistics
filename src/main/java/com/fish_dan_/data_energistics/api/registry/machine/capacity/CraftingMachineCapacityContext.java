package com.fish_dan_.data_energistics.api.registry.machine.capacity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Ephemeral server-thread context for one remaining-capacity observation.
 *
 * <p>
 * The machine, level and prototype remain owned by the caller. Adapters may inspect them only during the callback and
 * must not retain or mutate them.
 * </p>
 *
 * @param level           server level containing the machine
 * @param machinePosition exact machine position
 * @param inputSide       machine face reached by the pattern provider
 * @param machine         exact live block entity matched by the registration
 * @param patternDetails  exact pattern being considered for dispatch
 * @param prototype       read-only exact per-craft input prototype
 * @param requestedCrafts positive number of logical crafts currently eligible for this target; larger reported
 *                        capacities are safely limited to this value
 */
public record CraftingMachineCapacityContext(Level level,
                                             BlockPos machinePosition,
                                             Direction inputSide,
                                             BlockEntity machine,
                                             IPatternDetails patternDetails,
                                             KeyCounter[] prototype,
                                             long requestedCrafts) {

    public CraftingMachineCapacityContext {
        if (requestedCrafts <= 0L) {
            throw new IllegalArgumentException("Requested crafting machine capacity must be positive");
        }
    }
}
