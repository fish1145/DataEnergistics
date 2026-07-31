package com.fish_dan_.data_energistics.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.blockentity.networking.ControllerBlockEntity;

import java.util.Objects;

/**
 * Provides the shared, side-effect-free Channel Hub topology predicate used by pathing compatibility mixins.
 */
public final class ChannelHubGrid {

    private ChannelHubGrid() {}

    /**
     * Builds the allocator decision used by both AE2 pathing and optional external max-flow guards.
     *
     * @param grid                     grid whose topology is being assigned
     * @param externalMaxFlowAvailable whether the caller is an active external max-flow integration
     * @return authoritative allocation decision
     */
    public static ChannelHubAllocationPolicy.Decision decide(IGrid grid, boolean externalMaxFlowAvailable) {
        Objects.requireNonNull(grid, "grid");
        boolean hasHub = false;
        int normalControllerCount = 0;
        int overloadedControllerCount = 0;
        for (IGridNode node : grid.getNodes()) {
            Object owner = node.getOwner();
            if (owner instanceof ChannelHubHost) {
                hasHub = true;
            }
            if (owner instanceof ControllerBlockEntity) {
                if (owner instanceof ChannelHubControllerSource) {
                    overloadedControllerCount++;
                } else {
                    normalControllerCount++;
                }
            }
        }

        var pathingService = grid.getPathingService();
        return ChannelHubAllocationPolicy.decide(new ChannelHubAllocationPolicy.Topology(
                hasHub,
                normalControllerCount,
                overloadedControllerCount,
                pathingService.getControllerState(),
                pathingService.getChannelMode(),
                externalMaxFlowAvailable));
    }

    /**
     * Tests whether a matching external max-flow result must yield to Channel Hub shared-pool semantics.
     */
    public static boolean shouldBypassExternalMaxFlow(IGrid grid) {
        return decide(grid, true).bypassExternalMaxFlow();
    }
}
