package com.fish_dan_.data_energistics.ae2.grid;

import net.minecraft.core.BlockPos;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingService;
import appeng.blockentity.networking.ControllerBlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Resolves every controller's total supply and converts it to the domain capacity available after native physical
 * pathing
 * runs.
 */
public final class ControllerChannelCapacity implements TowerChannelCapacity {

    private static final int FACES_PER_CONTROLLER = 6;
    private static final int CHANNELS_PER_CONTROLLER_FACE = 32;

    /**
     * The calculator is owned by one tower domain, so one scalar entry is sufficient and does not retain a global Grid
     * partition. The pathing service identity protects against service replacement during a Grid lifecycle transition.
     */
    @Nullable
    private IGrid cachedGrid;

    @Nullable
    private IPathingService cachedPathingService;

    private long cachedPathingRevision = Long.MIN_VALUE;

    @Nullable
    private ControllerState cachedControllerState;

    @Nullable
    private ChannelMode cachedChannelMode;

    private int cachedCapacity;
    private boolean cacheValid;

    /**
     * Reads the authoritative pathing state and controller nodes from the supplied grid.
     *
     * @param grid grid whose capacity is requested
     * @return shared channel capacity
     */
    @Override
    public int calculate(IGrid grid) {
        IPathingService pathingService = grid.getPathingService();
        ControllerState controllerState = pathingService.getControllerState();
        ChannelMode channelMode = pathingService.getChannelMode();
        if (!(pathingService instanceof PathingTopologyRevision revisionBridge)) {
            return calculateGridSnapshot(grid, controllerState, channelMode);
        }

        long pathingRevision = revisionBridge.dataEnergistics$pathingTopologyRevision();
        if (this.cacheValid && this.cachedGrid == grid && this.cachedPathingService == pathingService && this.cachedPathingRevision == pathingRevision && this.cachedControllerState == controllerState && this.cachedChannelMode == channelMode) {
            return this.cachedCapacity;
        }

        int capacity = calculateGridSnapshot(grid, controllerState, channelMode);
        this.cachedGrid = grid;
        this.cachedPathingService = pathingService;
        this.cachedPathingRevision = pathingRevision;
        this.cachedControllerState = controllerState;
        this.cachedChannelMode = channelMode;
        this.cachedCapacity = capacity;
        this.cacheValid = true;
        return capacity;
    }

    /**
     * Computes one snapshot without touching the revision cache.
     *
     * @param grid            grid whose controllers are requested
     * @param controllerState current controller state
     * @param channelMode     current channel mode
     * @return total channel capacity
     */
    private int calculateGridSnapshot(IGrid grid,
                                      ControllerState controllerState,
                                      ChannelMode channelMode) {
        if (controllerState == ControllerState.CONTROLLER_CONFLICT || controllerState == ControllerState.NO_CONTROLLER || channelMode == ChannelMode.INFINITE) {
            return calculate(controllerState, channelMode, Set.of());
        }

        Set<Object> controllerOwners = Collections.newSetFromMap(new IdentityHashMap<>());
        int totalSupply = 0;
        for (IGridNode node : grid.getNodes()) {
            Object owner = node.getOwner();
            if (!(owner instanceof ControllerChannelSupply) && !(owner instanceof ControllerBlockEntity)) {
                continue;
            }
            if (!controllerOwners.add(owner)) {
                continue;
            }

            int controllerSupply = owner instanceof ControllerChannelSupply supply ? supply.totalChannelSupply(channelMode) : supplyFromControllerNode(node, channelMode);
            totalSupply = Math.addExact(totalSupply, requireNonNegativeSupply(owner, controllerSupply));
        }
        if (controllerOwners.isEmpty()) {
            throw new IllegalArgumentException("An online controller grid must contain at least one controller owner");
        }
        return totalSupply;
    }

    /**
     * Applies controller-state and controller-count rules to an explicit snapshot.
     *
     * @param controllerState     controller validation result
     * @param channelMode         active AE channel multiplier
     * @param controllerPositions controller block positions belonging to the same grid
     * @return shared channel capacity
     */
    @Override
    public int calculate(ControllerState controllerState,
                         ChannelMode channelMode,
                         Iterable<BlockPos> controllerPositions) {
        if (controllerState == ControllerState.CONTROLLER_CONFLICT) {
            return 0;
        }
        if (channelMode == ChannelMode.INFINITE) {
            return Integer.MAX_VALUE;
        }
        if (controllerState == ControllerState.NO_CONTROLLER) {
            return Math.multiplyExact(CHANNELS_PER_CONTROLLER_FACE, channelMode.getCableCapacityFactor());
        }

        Set<BlockPos> positions = immutablePositionSet(controllerPositions);
        if (positions.isEmpty()) {
            throw new IllegalArgumentException("An online controller grid must contain at least one controller position");
        }

        return Math.multiplyExact(positions.size(), nativeControllerSupply(channelMode));
    }

    /**
     * Uses a positive capacity reported by the controller node as its per-face capacity. Native AE controllers report
     * zero
     * because they cannot carry channels themselves, so they use the native dense-controller fallback.
     */
    private static int supplyFromControllerNode(IGridNode node, ChannelMode channelMode) {
        int reportedFaceSupply = node.getMaxChannels();
        if (reportedFaceSupply < 0) {
            throw new IllegalStateException("A controller grid node reported a negative channel capacity");
        }
        int faceSupply = reportedFaceSupply == 0 ? Math.multiplyExact(CHANNELS_PER_CONTROLLER_FACE, channelMode.getCableCapacityFactor()) : reportedFaceSupply;
        return Math.multiplyExact(FACES_PER_CONTROLLER, faceSupply);
    }

    private static int nativeControllerSupply(ChannelMode channelMode) {
        int faceSupply = Math.multiplyExact(CHANNELS_PER_CONTROLLER_FACE, channelMode.getCableCapacityFactor());
        return Math.multiplyExact(FACES_PER_CONTROLLER, faceSupply);
    }

    private static int requireNonNegativeSupply(Object owner, int supply) {
        if (supply < 0) {
            throw new IllegalStateException("Controller channel supply must be non-negative: " + owner.getClass().getName());
        }
        return supply;
    }

    /**
     * Normalizes mutable or repeated position inputs before controller counting.
     *
     * @param controllerPositions positions supplied by the caller
     * @return immutable-position set used by the controller calculation
     */
    private static Set<BlockPos> immutablePositionSet(Iterable<BlockPos> controllerPositions) {
        Set<BlockPos> positions = new HashSet<>();
        for (BlockPos position : controllerPositions) {
            positions.add(position.immutable());
        }
        return positions;
    }
}
