package com.fish_dan_.data_energistics.common.crafting.trinity.execution.route;

import com.fish_dan_.data_energistics.ae2.grid.VirtualGridBridge;
import com.fish_dan_.data_energistics.ae2.grid.VirtualGridNode;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the physical and effective-service identities of one Trinity crafting node.
 * <p>
 * Default route resolver backed by the typed virtual-membership contract injected into AE grid nodes.
 */
public final class TrinityCraftingRouteResolver {

    /**
     * Resolves an immutable owning-grid and effective-service route while preserving the current virtual-membership
     * generation without changing node or grid state.
     *
     * @param node       access-hatch grid node, or {@code null} before attachment
     * @param leaseEpoch current host lease epoch
     * @return resolved route, or {@code null} while the node has no owning grid or remains an inactive subordinate
     */
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
