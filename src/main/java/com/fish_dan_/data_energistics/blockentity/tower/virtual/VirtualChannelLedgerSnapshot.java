package com.fish_dan_.data_energistics.blockentity.tower.virtual;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Immutable view of one completed virtual channel allocation pass.
 *
 * @param totalCapacity            configured total channel capacity
 * @param physicalChannelUsage     physical channels reserved before virtual allocation
 * @param virtualChannelUsage      charged virtual leases
 * @param remainingChannelCapacity remaining finite capacity, or empty when unlimited
 * @param bindings                 binding results in allocation order
 * @param <B>                      binding key type
 * @param <N>                      node key type
 */
public record VirtualChannelLedgerSnapshot<B, N>(VirtualChannelCapacity totalCapacity,
                                                 long physicalChannelUsage,
                                                 long virtualChannelUsage,
                                                 OptionalLong remainingChannelCapacity,
                                                 List<VirtualChannelBindingAllocation<B, N>> bindings) {

    /**
     * Validates counters and defensively copies binding results.
     */
    public VirtualChannelLedgerSnapshot {
        if (physicalChannelUsage < 0 || virtualChannelUsage < 0) {
            throw new IllegalArgumentException("Virtual channel usage counters must not be negative");
        }
        if (remainingChannelCapacity.isPresent() && remainingChannelCapacity.getAsLong() < 0) {
            throw new IllegalArgumentException("Remaining virtual channel capacity must not be negative");
        }
        bindings = List.copyOf(bindings);
    }

    /**
     * Finds the allocation result for one binding.
     *
     * @param bindingKey stable binding key
     * @return allocation result when the binding exists
     */
    public Optional<VirtualChannelBindingAllocation<B, N>> allocationFor(B bindingKey) {
        return this.bindings.stream()
                .filter(binding -> binding.bindingKey().equals(bindingKey))
                .findFirst();
    }
}
