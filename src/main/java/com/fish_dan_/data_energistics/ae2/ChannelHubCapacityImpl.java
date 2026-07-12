package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.Data_Energistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingService;
import appeng.blockentity.networking.ControllerBlockEntity;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Counts geometrically exposed controller faces and converts them to the dense-channel capacity shared by every hub on
 * the grid.
 */
public final class ChannelHubCapacityImpl implements ChannelHubCapacity {

    /**
     * Number of base channels supplied by one exposed controller face.
     */
    private static final int CHANNELS_PER_EXPOSED_FACE = 32;

    /**
     * Reads the authoritative pathing state and controller nodes from the supplied grid.
     *
     * @param grid grid whose capacity is requested
     * @return shared channel capacity
     */
    @Override
    public int calculate(IGrid grid) {
        Objects.requireNonNull(grid, "grid");
        IPathingService pathingService = grid.getPathingService();
        Set<BlockPos> controllerPositions = new HashSet<>();
        for (IGridNode node : grid.getMachineNodes(ControllerBlockEntity.class)) {
            if (!(node.getOwner() instanceof ControllerBlockEntity controller)) {
                throw new IllegalStateException("Controller grid-node collection contained a non-controller owner");
            }
            controllerPositions.add(controller.getBlockPos().immutable());
        }
        return calculate(pathingService.getControllerState(), pathingService.getChannelMode(), controllerPositions);
    }

    /**
     * Applies controller-state and geometry rules to an explicit snapshot.
     *
     * @param controllerState     controller validation result
     * @param channelMode         active AE channel multiplier
     * @param controllerPositions controller block positions belonging to the same grid
     * @return shared channel capacity
     */
    @Override
    public int calculate(ControllerState controllerState, ChannelMode channelMode,
                         Iterable<BlockPos> controllerPositions) {
        Objects.requireNonNull(controllerState, "controllerState");
        Objects.requireNonNull(channelMode, "channelMode");
        Objects.requireNonNull(controllerPositions, "controllerPositions");

        if (controllerState == ControllerState.CONTROLLER_CONFLICT) {
            Data_Energistics.LOGGER.error("Cannot expose channel-hub capacity while the AE controller is conflicted");
            return 0;
        }
        if (channelMode == ChannelMode.INFINITE) {
            return Integer.MAX_VALUE;
        }
        if (controllerState == ControllerState.NO_CONTROLLER) {
            return Math.multiplyExact(CHANNELS_PER_EXPOSED_FACE, channelMode.getCableCapacityFactor());
        }

        Set<BlockPos> positions = immutablePositionSet(controllerPositions);
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("An online controller grid must contain at least one controller position");
        }

        int exposedFaces = 0;
        for (BlockPos position : positions) {
            for (Direction direction : Direction.values()) {
                if (!positions.contains(position.relative(direction))) {
                    exposedFaces = Math.addExact(exposedFaces, 1);
                }
            }
        }

        int baseCapacity = Math.multiplyExact(exposedFaces, CHANNELS_PER_EXPOSED_FACE);
        return Math.multiplyExact(baseCapacity, channelMode.getCableCapacityFactor());
    }

    /**
     * Normalizes mutable or repeated position inputs before adjacency checks.
     *
     * @param controllerPositions positions supplied by the caller
     * @return immutable-position set used by the geometry calculation
     */
    private static Set<BlockPos> immutablePositionSet(Iterable<BlockPos> controllerPositions) {
        Set<BlockPos> positions = new HashSet<>();
        for (BlockPos position : controllerPositions) {
            positions.add(Objects.requireNonNull(position, "controller position").immutable());
        }
        return positions;
    }
}
