package com.fish_dan_.data_energistics.blockentity.tower.virtual;

import java.util.OptionalLong;

/**
 * Describes the total channel budget available to a virtual channel ledger.
 *
 * <p>
 * An empty finite limit represents an unlimited budget without relying on a numeric sentinel, so physical usage at
 * {@link Long#MAX_VALUE} cannot overflow capacity arithmetic.
 * </p>
 *
 * @param finiteLimit finite non-negative channel limit, or empty for unlimited capacity
 */
public record VirtualChannelCapacity(OptionalLong finiteLimit) {

    /**
     * Validates the explicit finite-or-unlimited representation.
     */
    public VirtualChannelCapacity {
        if (finiteLimit.isPresent() && finiteLimit.getAsLong() < 0) {
            throw new IllegalArgumentException("Virtual channel capacity must not be negative");
        }
    }

    /**
     * Creates a finite channel budget.
     *
     * @param channelLimit non-negative channel count
     * @return finite capacity value
     */
    public static VirtualChannelCapacity limited(long channelLimit) {
        return new VirtualChannelCapacity(OptionalLong.of(channelLimit));
    }

    /**
     * Creates an unlimited channel budget.
     *
     * @return unlimited capacity value
     */
    public static VirtualChannelCapacity unlimited() {
        return new VirtualChannelCapacity(OptionalLong.empty());
    }

    /**
     * Checks whether the capacity has no finite upper bound.
     *
     * @return true for unlimited capacity
     */
    public boolean isUnlimited() {
        return this.finiteLimit.isEmpty();
    }
}
