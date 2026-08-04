package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model;

/**
 * Represents a provider capacity fact without confusing an unknown value with zero or an artificial upper bound.
 */
public sealed interface DispatchCapacity permits DispatchCapacity.Known, DispatchCapacity.Unknown {

    /**
     * A capacity that the provider can prove at capture time.
     *
     * <p>
     * Zero means that the target currently has no capacity; it never means unknown.
     * </p>
     *
     * @param logicalCrafts non-negative logical craft count
     */
    record Known(long logicalCrafts) implements DispatchCapacity {

        public Known {
            if (logicalCrafts < 0L) {
                throw new IllegalArgumentException("Known crafting dispatch capacity must not be negative");
            }
        }
    }

    /**
     * Capacity for which the provider exposes no safe numeric bound.
     */
    enum Unknown implements DispatchCapacity {
        /**
         * Shared value because unknown capacity carries no instance state.
         */
        INSTANCE
    }
}
