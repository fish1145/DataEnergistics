package com.fish_dan_.data_energistics.ae2;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;

/**
 * Internal typed invoker used by a subordinate bridge to mutate the primary grid's service registrations.
 *
 * <p>
 * Callers outside the bridge implementation should use {@link VirtualGridBridge}.
 * </p>
 */
public interface VirtualGridBridgeInternal extends VirtualGridBridge {

    /**
     * Registers one enabled node from its owning subordinate grid.
     *
     * @param sourceGrid physical grid retaining node identity
     * @param node       node to expose to primary services
     */
    void registerIncomingVirtualNode(IGrid sourceGrid, IGridNode node);

    /**
     * Removes one enabled node from primary services.
     *
     * @param sourceGrid physical grid retaining node identity
     * @param node       node to remove
     */
    void unregisterIncomingVirtualNode(IGrid sourceGrid, IGridNode node);
}
