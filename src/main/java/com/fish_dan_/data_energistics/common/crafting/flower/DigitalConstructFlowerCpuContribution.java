package com.fish_dan_.data_energistics.common.crafting.flower;

import appeng.api.config.CpuSelectionMode;

import java.util.Objects;

/**
 * Contribution from one formed Digital Construct Flower structure section to the host CPU profile.
 *
 * <p>
 * Substructures use this data object to add crafting storage, co-processors, and virtual CPU partitions without
 * reaching into the host's runtime state.
 */
public record DigitalConstructFlowerCpuContribution(long storageBytes,
                                                    int coProcessors,
                                                    int partitionCount,
                                                    CpuSelectionMode selectionMode) {

    public static final DigitalConstructFlowerCpuContribution EMPTY = new DigitalConstructFlowerCpuContribution(0L, 0, 0, CpuSelectionMode.ANY);

    public DigitalConstructFlowerCpuContribution {
        if (storageBytes < 0) {
            throw new IllegalArgumentException("CPU contribution storage bytes must not be negative");
        }
        if (coProcessors < 0) {
            throw new IllegalArgumentException("CPU contribution co-processors must not be negative");
        }
        if (partitionCount < 0) {
            throw new IllegalArgumentException("CPU contribution partition count must not be negative");
        }
        selectionMode = Objects.requireNonNull(selectionMode, "selectionMode");
    }

    /**
     * Creates a contribution using AE2's default auto-selection mode.
     *
     * @param storageBytes   crafting bytes added by the structure section
     * @param coProcessors   co-processors added by the structure section
     * @param partitionCount virtual CPU partitions added by the structure section
     * @return validated contribution data
     */
    public static DigitalConstructFlowerCpuContribution of(long storageBytes, int coProcessors, int partitionCount) {
        return new DigitalConstructFlowerCpuContribution(storageBytes, coProcessors, partitionCount, CpuSelectionMode.ANY);
    }
}
