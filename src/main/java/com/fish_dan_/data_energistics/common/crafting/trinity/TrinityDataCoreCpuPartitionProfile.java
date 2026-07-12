package com.fish_dan_.data_energistics.common.crafting.trinity;

import appeng.api.config.CpuSelectionMode;

/**
 * Resolved resources for the reserved CPU or one virtual Trinity Data Core worker.
 *
 * <p>
 * {@code totalPartitions} is the worker capacity. CPU number zero is reserved, so valid numbers are zero through the
 * worker capacity, inclusive.
 */
public record TrinityDataCoreCpuPartitionProfile(int index,
                                                 int totalPartitions,
                                                 long storageBytes,
                                                 int coProcessors,
                                                 CpuSelectionMode selectionMode) {

    public TrinityDataCoreCpuPartitionProfile {
        if (totalPartitions <= 0) {
            throw new IllegalArgumentException("CPU worker capacity must be positive");
        }
        if (totalPartitions > TrinityDataCoreCpuContribution.MAX_PARTITION_COUNT) {
            throw new IllegalArgumentException(
                    "CPU worker capacity must not exceed " + TrinityDataCoreCpuContribution.MAX_PARTITION_COUNT);
        }
        if (index < 0 || index > totalPartitions) {
            throw new IllegalArgumentException("CPU number is out of range: " + index);
        }
        if (storageBytes <= 0) {
            throw new IllegalArgumentException("CPU partition storage bytes must be positive");
        }
        if (coProcessors < 0) {
            throw new IllegalArgumentException("CPU partition co-processors must not be negative");
        }
    }
}
