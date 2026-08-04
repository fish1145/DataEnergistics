package com.fish_dan_.data_energistics.common.crafting.trinity.execution.route;

import com.fish_dan_.data_energistics.ae2.VirtualGridBridge;
import com.fish_dan_.data_energistics.ae2.VirtualGridNode;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import org.jetbrains.annotations.Nullable;

/**
 * Default route resolver backed by the typed virtual-membership contract injected into AE grid nodes.
 */
public final class TrinityCraftingRouteResolverImpl implements TrinityCraftingRouteResolver {

    /**
     * Resolves the owning grid and active virtual primary while preserving the current membership generation.
     */
    @Override
    @Nullable
    public TrinityCraftingExecutionRoute resolve(@Nullable IGridNode node, long leaseEpoch) {
        if (leaseEpoch < 0L) {
            throw new IllegalArgumentException("Trinity crafting route lease epoch must not be negative");
        }
        if (node == null) {
            return null;
        }
        IGrid owningGrid = node.getGrid();
        if (owningGrid == null) {
            return null;
        }
        if (!(node instanceof VirtualGridNode virtualNode)) {
            return new TrinityCraftingExecutionRoute(owningGrid, owningGrid, leaseEpoch, 0L);
        }

        long membershipGeneration = virtualNode.virtualMembershipGeneration();
        IGrid primaryGrid = virtualNode.virtualPrimaryGrid();
        if (!virtualNode.isVirtualMemberActive()) {
            if (primaryGrid != null ||
                    owningGrid instanceof VirtualGridBridge owningBridge &&
                            owningBridge.virtualPrimaryGrid() != null) {
                return null;
            }
            return new TrinityCraftingExecutionRoute(
                    owningGrid,
                    owningGrid,
                    leaseEpoch,
                    membershipGeneration);
        }
        if (primaryGrid == null) {
            return null;
        }
        if (!(primaryGrid instanceof VirtualGridBridge primaryBridge) ||
                !primaryBridge.containsIncomingVirtualMember(node)) {
            return null;
        }
        return new TrinityCraftingExecutionRoute(
                owningGrid,
                primaryGrid,
                leaseEpoch,
                membershipGeneration);
    }
}
