package com.fish_dan_.data_energistics.blockentity.tower.virtual;

import java.util.List;

/**
 * Immutable allocation result for one bound device.
 *
 * @param bindingKey stable binding key
 * @param source     binding allocation queue
 * @param fifoOrder  explicit FIFO token within the queue
 * @param enabled    true when the binding participates in allocation
 * @param nodes      node results in explicit stable order
 * @param <B>        binding key type
 * @param <N>        node key type
 */
public record VirtualChannelBindingAllocation<B, N>(B bindingKey, VirtualChannelBindingSource source,
                                                    long fifoOrder, boolean enabled,
                                                    List<VirtualChannelNodeAllocation<N>> nodes) {

    /**
     * Defensively copies the node allocation list.
     */
    public VirtualChannelBindingAllocation {
        if (fifoOrder < 0) {
            throw new IllegalArgumentException("Virtual channel binding FIFO order must not be negative");
        }
        nodes = List.copyOf(nodes);
    }

    /**
     * Counts charged node leases owned by this binding.
     *
     * @return charged virtual channel count
     */
    public long leasedChannelCount() {
        return this.nodes.stream()
                .filter(node -> node.state() == VirtualChannelNodeState.LEASED)
                .count();
    }
}
