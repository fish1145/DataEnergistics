package com.fish_dan_.data_energistics.blockentity.tower.virtual;

/**
 * Allocates virtual channels without creating AE grid connections.
 *
 * <p>
 * The ledger reserves physical usage first, then visits manual and automatic binding queues in explicit FIFO order.
 * It is intentionally generic so the integration layer can provide stable runtime identity wrappers without exposing
 * AE or Minecraft types to the allocation algorithm.
 * </p>
 *
 * @param <B> binding key type with stable equality and hash semantics
 * @param <N> node key type with stable equality and hash semantics
 */
public interface VirtualChannelLedger<B, N> {

    /**
     * Replaces the total finite or unlimited channel budget.
     *
     * @param capacity new total capacity
     */
    void setTotalCapacity(VirtualChannelCapacity capacity);

    /**
     * Replaces the physical channel count that takes priority over virtual leases.
     *
     * @param channelUsage non-negative physical channel usage
     */
    void setPhysicalChannelUsage(long channelUsage);

    /**
     * Adds a device binding or refreshes its current nodes and enabled state.
     *
     * <p>
     * Source, binding FIFO order, and existing node stable-order tokens are immutable for the lifetime of a key.
     * Callers must remove and re-add a binding when its ordering identity genuinely changes.
     * </p>
     *
     * @param request immutable binding request
     */
    void upsertBinding(VirtualChannelBindingRequest<B, N> request);

    /**
     * Removes one binding and releases all of its leases.
     *
     * @param bindingKey binding key
     * @return true when a binding was removed
     */
    boolean removeBinding(B bindingKey);

    /**
     * Enables or disables one bound device without changing its FIFO position.
     *
     * @param bindingKey binding key
     * @param enabled    desired participation state
     * @return true when the binding exists
     */
    boolean setBindingEnabled(B bindingKey, boolean enabled);

    /**
     * Computes an immutable allocation snapshot from current requests and capacity.
     *
     * @return deterministic allocation snapshot
     */
    VirtualChannelLedgerSnapshot<B, N> snapshot();
}
