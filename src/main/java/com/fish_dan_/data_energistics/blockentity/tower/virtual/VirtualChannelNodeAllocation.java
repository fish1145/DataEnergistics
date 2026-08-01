package com.fish_dan_.data_energistics.blockentity.tower.virtual;

/**
 * Immutable allocation result for one node.
 *
 * @param nodeKey         stable node key
 * @param stableOrder     explicit order within the binding
 * @param requiresChannel true when a leased node consumes one channel
 * @param state           current allocation state
 * @param <N>             node key type
 */
public record VirtualChannelNodeAllocation<N>(N nodeKey, long stableOrder, boolean requiresChannel,
                                              VirtualChannelNodeState state) {

    /**
     * Validates the immutable allocation result.
     */
    public VirtualChannelNodeAllocation {
        if (stableOrder < 0) {
            throw new IllegalArgumentException("Virtual channel node order must not be negative");
        }
        if (state == VirtualChannelNodeState.LEASED && !requiresChannel) {
            throw new IllegalArgumentException("A node without channel requirements must not hold a charged lease");
        }
        if (state == VirtualChannelNodeState.AVAILABLE_WITHOUT_CHANNEL && requiresChannel) {
            throw new IllegalArgumentException("A channel-requiring node cannot be marked channel-free");
        }
    }
}
