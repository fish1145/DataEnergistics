package com.fish_dan_.data_energistics.blockentity.tower.virtual;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Supplies one device binding and all capability nodes currently exposed by that device.
 *
 * @param bindingKey stable binding key supplied by the integration layer
 * @param source     allocation queue selected by the binding origin
 * @param fifoOrder  explicit non-negative FIFO token within the source queue
 * @param enabled    true when the device may receive allocations
 * @param nodes      current node requests; the list is defensively copied
 * @param <B>        binding key type
 * @param <N>        node key type
 */
public record VirtualChannelBindingRequest<B, N>(B bindingKey, VirtualChannelBindingSource source,
                                                 long fifoOrder, boolean enabled,
                                                 List<VirtualChannelNodeRequest<N>> nodes) {

    /**
     * Validates binding metadata and rejects ambiguous node ordering.
     */
    public VirtualChannelBindingRequest {
        if (fifoOrder < 0) {
            throw new IllegalArgumentException("Virtual channel binding FIFO order must not be negative");
        }
        nodes = List.copyOf(nodes);
        Set<N> nodeKeys = new HashSet<>();
        Set<Long> nodeOrders = new HashSet<>();
        for (VirtualChannelNodeRequest<N> node : nodes) {
            if (!nodeKeys.add(node.nodeKey())) {
                throw new IllegalArgumentException("Duplicate virtual channel node key: " + node.nodeKey());
            }
            if (!nodeOrders.add(node.stableOrder())) {
                throw new IllegalArgumentException("Duplicate virtual channel node order: " + node.stableOrder());
            }
        }
    }
}
