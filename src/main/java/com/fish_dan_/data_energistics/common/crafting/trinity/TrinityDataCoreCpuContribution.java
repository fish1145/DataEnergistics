package com.fish_dan_.data_energistics.common.crafting.trinity;

import appeng.api.config.CpuSelectionMode;

/**
 * Contribution from one formed Trinity Data Core structure section to the host CPU profile.
 *
 * <p>
 * Substructures use this data object to add crafting storage, co-processors, and virtual CPU partitions without
 * reaching into the host's runtime state.
 */
public record TrinityDataCoreCpuContribution(long storageBytes,
                                             int coProcessors,
                                             int partitionCount,
                                             CpuSelectionMode selectionMode) {

    public static final int MAX_PARTITION_COUNT = 256;
    public static final TrinityDataCoreCpuContribution EMPTY = new TrinityDataCoreCpuContribution(0L, 0, 0, CpuSelectionMode.ANY);

    public TrinityDataCoreCpuContribution {
        if (storageBytes < 0) {
            throw new IllegalArgumentException("CPU contribution storage bytes must not be negative");
        }
        if (coProcessors < 0) {
            throw new IllegalArgumentException("CPU contribution co-processors must not be negative");
        }
        if (partitionCount < 0) {
            throw new IllegalArgumentException("CPU contribution partition count must not be negative");
        }
        if (partitionCount > MAX_PARTITION_COUNT) {
            throw new IllegalArgumentException(
                    "CPU contribution partition count must not exceed " + MAX_PARTITION_COUNT);
        }
    }

    /**
     * Creates a contribution using AE2's default auto-selection mode.
     *
     * @param storageBytes   crafting bytes added by the structure section
     * @param coProcessors   co-processors added by the structure section
     * @param partitionCount virtual CPU partitions added by the structure section
     * @return validated contribution data
     */
    public static TrinityDataCoreCpuContribution of(long storageBytes, int coProcessors, int partitionCount) {
        return new TrinityDataCoreCpuContribution(storageBytes, coProcessors, partitionCount, CpuSelectionMode.ANY);
    }
}
