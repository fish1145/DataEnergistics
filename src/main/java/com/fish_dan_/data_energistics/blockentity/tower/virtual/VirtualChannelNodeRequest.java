package com.fish_dan_.data_energistics.blockentity.tower.virtual;

/**
 * Describes one capability node exposed by a bound device.
 *
 * @param nodeKey         stable key supplied by the integration layer
 * @param stableOrder     explicit non-negative order within the binding
 * @param requiresChannel true when exposing the node consumes one virtual channel
 * @param <N>             node key type
 */
public record VirtualChannelNodeRequest<N>(N nodeKey, long stableOrder, boolean requiresChannel) {

    /**
     * Validates the stable node identity and ordering token.
     */
    public VirtualChannelNodeRequest {
        if (stableOrder < 0) {
            throw new IllegalArgumentException("Virtual channel node order must not be negative");
        }
    }
}
