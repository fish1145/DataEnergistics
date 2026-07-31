package com.fish_dan_.data_energistics.ae2;

import net.minecraft.core.BlockPos;

import appeng.api.networking.IGrid;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;

/**
 * Calculates the shared channel budget exposed through all channel hubs on one AE grid.
 */
public interface ChannelHubCapacity {

    /**
     * Calculates the capacity from the current controller state, channel mode and controller geometry of a grid.
     *
     * @param grid grid whose hubs share the resulting budget
     * @return shared channel capacity
     */
    int calculate(IGrid grid);

    /**
     * Calculates capacity from an explicit controller snapshot so geometry rules can be verified independently from AE
     * runtime objects.
     *
     * @param controllerState     controller validation result
     * @param channelMode         active AE channel multiplier
     * @param controllerPositions controller block positions belonging to the same grid
     * @return shared channel capacity
     */
    int calculate(ControllerState controllerState, ChannelMode channelMode, Iterable<BlockPos> controllerPositions);

    /**
     * Combines ordinary exposed-face capacity with independently supplied overloaded-controller capacity.
     *
     * @param controllerState            controller validation result
     * @param channelMode                active AE channel multiplier
     * @param normalControllerPositions  ordinary controllers whose exposed faces provide channels
     * @param allControllerPositions     all ordinary and overloaded controller blocks used for adjacency checks
     * @param overloadedControllerSupply already-scaled sum supplied by overloaded controllers
     * @return shared channel capacity
     */
    int calculateCombined(ControllerState controllerState,
                          ChannelMode channelMode,
                          Iterable<BlockPos> normalControllerPositions,
                          Iterable<BlockPos> allControllerPositions,
                          int overloadedControllerSupply);
}
