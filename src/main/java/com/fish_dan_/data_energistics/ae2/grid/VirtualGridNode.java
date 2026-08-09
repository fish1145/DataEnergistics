package com.fish_dan_.data_energistics.ae2.grid;

import appeng.api.networking.IGrid;
import org.jetbrains.annotations.Nullable;

/**
 * Exposes the runtime virtual-membership state injected into an AE grid node.
 *
 * <p>
 * The node keeps its original {@link IGrid} identity. This state only redirects its active power/channel view to
 * the primary grid while the node is registered as a virtual member there.
 * </p>
 */
public interface VirtualGridNode {

    /**
     * Returns the primary grid currently serving this node virtually.
     *
     * @return primary grid, or {@code null} when the node is not subordinate
     */
    @Nullable
    IGrid virtualPrimaryGrid();

    /**
     * Tests whether this device is enabled and registered in the primary grid services.
     *
     * @return whether the virtual member is active
     */
    boolean isVirtualMemberActive();

    /**
     * Returns the monotonic generation of this node's virtual membership state.
     *
     * @return generation incremented after every effective primary or active-state change
     */
    long virtualMembershipGeneration();

    /**
     * Updates the node's virtual service view without changing its owning grid.
     *
     * @param primaryGrid primary grid, or {@code null} to release the node
     * @param active      whether the device is registered in primary-grid services
     */
    void updateVirtualMembership(@Nullable IGrid primaryGrid, boolean active);
}
