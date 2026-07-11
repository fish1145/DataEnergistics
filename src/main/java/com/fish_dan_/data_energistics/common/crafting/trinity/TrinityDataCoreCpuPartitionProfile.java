package com.fish_dan_.data_energistics.common.crafting.trinity;

import appeng.api.config.CpuSelectionMode;

/**
 * Resolved resource slice for one virtual Trinity Data Core crafting CPU.
 *
 * <p>
 * Runtime CPU objects copy this immutable profile so structure changes can rebuild partitions deterministically.
 */
public record TrinityDataCoreCpuPartitionProfile(int index,
                                                 int totalPartitions,
                                                 long storageBytes,
                                                 int coProcessors,
                                                 CpuSelectionMode selectionMode) {

    public TrinityDataCoreCpuPartitionProfile {
        if (totalPartitions <= 0) {
            throw new IllegalArgumentException("CPU partition total must be positive");
        }
        if (index < 0 || index >= totalPartitions) {
            throw new IllegalArgumentException("CPU partition index is out of range: " + index);
        }
        if (storageBytes <= 0) {
            throw new IllegalArgumentException("CPU partition storage bytes must be positive");
        }
        if (coProcessors < 0) {
            throw new IllegalArgumentException("CPU partition co-processors must not be negative");
        }
    }
}
