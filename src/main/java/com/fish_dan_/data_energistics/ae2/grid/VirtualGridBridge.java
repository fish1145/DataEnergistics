package com.fish_dan_.data_energistics.ae2.grid;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * Provides typed access to the virtual service bridge injected into every AE grid.
 *
 * <p>
 * A subordinate grid remains a distinct physical/pathing grid. Its enabled nodes are additionally registered with
 * every primary-grid service except pathing and the tower-domain service.
 * </p>
 */
public interface VirtualGridBridge {

    /**
     * Returns the primary grid receiving delegated service access.
     *
     * @return primary grid, or {@code null} for an independent grid
     */
    @Nullable
    IGrid virtualPrimaryGrid();

    /**
     * Returns the nodes currently registered in this grid from subordinate grids.
     *
     * @return identity-based immutable member snapshot
     */
    Set<IGridNode> incomingVirtualMembers();

    /**
     * Tests whether one physical node from a subordinate grid is currently registered with this grid's services.
     *
     * @param node candidate subordinate node
     * @return whether the node is an active incoming virtual member
     */
    boolean containsIncomingVirtualMember(IGridNode node);

    /**
     * Returns the number of physical nodes owned by this grid, excluding incoming virtual members.
     *
     * @return physical node count
     */
    int physicalNodeCount();

    /**
     * Atomically attaches or updates this subordinate grid.
     *
     * @param primaryGrid primary grid that receives service registrations
     * @param allNodes    all currently loaded nodes in this subordinate grid
     * @param activeNodes enabled nodes; must be a subset of {@code allNodes}
     * @throws VirtualGridBridgeException when validation or service registration fails
     */
    void replaceVirtualMembers(IGrid primaryGrid,
                               Collection<? extends IGridNode> allNodes,
                               Collection<? extends IGridNode> activeNodes);

    /**
     * Releases one physical node that is leaving this subordinate grid.
     *
     * @param node node being removed from the physical grid
     */
    void releasePhysicalNode(IGridNode node);

    /**
     * Releases the complete subordinate relationship and restores local service access.
     */
    void clearVirtualMembers();
}
