package com.fish_dan_.data_energistics.ae2;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingService;
import appeng.blockentity.networking.ControllerBlockEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * Counts geometrically exposed controller faces and converts them to the total domain capacity available after native
 * physical pathing runs.
 */
public final class TowerChannelCapacityImpl implements TowerChannelCapacity {

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
        IPathingService pathingService = grid.getPathingService();
        Set<BlockPos> allControllerPositions = new HashSet<>();
        Set<BlockPos> normalControllerPositions = new HashSet<>();
        int cableCapacityFactor = pathingService.getChannelMode().getCableCapacityFactor();
        int overloadedControllerSupply = 0;
        for (IGridNode node : grid.getNodes()) {
            if (!(node.getOwner() instanceof ControllerBlockEntity controller)) {
                continue;
            }
            BlockPos position = controller.getBlockPos().immutable();
            allControllerPositions.add(position);
            if (controller instanceof TowerOverloadedChannelSource source) {
                int supply = source.getVirtualChannelSupply(cableCapacityFactor);
                if (supply < 0) {
                    throw new IllegalStateException("Overloaded controller supplied a negative channel capacity");
                }
                overloadedControllerSupply = Math.addExact(overloadedControllerSupply, supply);
            } else {
                normalControllerPositions.add(position);
            }
        }
        return calculateCombined(
                pathingService.getControllerState(),
                pathingService.getChannelMode(),
                normalControllerPositions,
                allControllerPositions,
                overloadedControllerSupply);
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
        Set<BlockPos> positions = immutablePositionSet(controllerPositions);
        return calculateCombined(controllerState, channelMode, positions, positions, 0);
    }

    /**
     * Applies ordinary geometry only to normal controllers and adds overloaded controllers' per-controller supply.
     */
    @Override
    public int calculateCombined(ControllerState controllerState,
                                 ChannelMode channelMode,
                                 Iterable<BlockPos> normalControllerPositions,
                                 Iterable<BlockPos> allControllerPositions,
                                 int overloadedControllerSupply) {
        if (controllerState == ControllerState.CONTROLLER_CONFLICT) {
            return 0;
        }
        if (channelMode == ChannelMode.INFINITE) {
            return Integer.MAX_VALUE;
        }
        if (controllerState == ControllerState.NO_CONTROLLER) {
            return Math.multiplyExact(CHANNELS_PER_EXPOSED_FACE, channelMode.getCableCapacityFactor());
        }

        if (overloadedControllerSupply < 0) {
            throw new IllegalArgumentException("Overloaded-controller supply must not be negative");
        }

        Set<BlockPos> allPositions = immutablePositionSet(allControllerPositions);
        Set<BlockPos> normalPositions = immutablePositionSet(normalControllerPositions);
        if (allPositions.isEmpty()) {
            throw new IllegalArgumentException("An online controller grid must contain at least one controller position");
        }
        if (!allPositions.containsAll(normalPositions)) {
            throw new IllegalArgumentException("Normal-controller positions must be included in all controller positions");
        }

        int exposedFaces = 0;
        for (BlockPos position : normalPositions) {
            for (Direction direction : Direction.values()) {
                if (!allPositions.contains(position.relative(direction))) {
                    exposedFaces = Math.addExact(exposedFaces, 1);
                }
            }
        }

        int baseCapacity = Math.multiplyExact(exposedFaces, CHANNELS_PER_EXPOSED_FACE);
        int normalCapacity = Math.multiplyExact(baseCapacity, channelMode.getCableCapacityFactor());
        return Math.addExact(normalCapacity, overloadedControllerSupply);
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
            positions.add(position.immutable());
        }
        return positions;
    }
}
