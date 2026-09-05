package com.fish_dan_.data_energistics.ae2.grid;

import appeng.api.networking.IGrid;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;

import net.minecraft.core.BlockPos;

/**
 * Calculates the total channel budget available to one tower network domain.
 */
public interface TowerChannelCapacity {

    /**
     * Calculates the capacity from the current controller state, channel mode and controllers of a grid.
     *
     * @param grid physical primary grid whose budget is requested
     * @return total channel capacity
     */
    int calculate(IGrid grid);

    /**
     * Calculates capacity from an explicit controller snapshot so controller-count rules can be verified independently
     * from AE runtime objects.
     *
     * <p>
     * Controller removal, Grid split or merge, and repath can expose an empty controller snapshot before the controller
     * state changes from online. An empty snapshot therefore uses the controllerless budget rather than requiring those
     * two observations to be synchronized.
     *
     * @param controllerState     controller validation result
     * @param channelMode         active AE channel multiplier
     * @param controllerPositions controller block positions belonging to the same grid
     * @return total channel capacity
     */
    int calculate(ControllerState controllerState, ChannelMode channelMode, Iterable<BlockPos> controllerPositions);
}
