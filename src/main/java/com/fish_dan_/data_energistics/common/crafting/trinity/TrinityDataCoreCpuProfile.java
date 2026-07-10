package com.fish_dan_.data_energistics.common.crafting.trinity;

import appeng.api.config.CpuSelectionMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregate CPU data for a formed Trinity Data Core host.
 *
 * <p>
 * The profile is built from named structure contributions and resolves them into stable virtual CPU partitions.
 */
public record TrinityDataCoreCpuProfile(long storageBytes,
                                        int coProcessors,
                                        int partitionCount,
                                        CpuSelectionMode selectionMode) {

    public static final TrinityDataCoreCpuProfile EMPTY = new TrinityDataCoreCpuProfile(0L, 0, 0, CpuSelectionMode.ANY);

    public TrinityDataCoreCpuProfile {
        if (storageBytes < 0) {
            throw new IllegalArgumentException("CPU profile storage bytes must not be negative");
        }
        if (coProcessors < 0) {
            throw new IllegalArgumentException("CPU profile co-processors must not be negative");
        }
        if (partitionCount < 0) {
            throw new IllegalArgumentException("CPU profile partition count must not be negative");
        }
        if (partitionCount > 0 && storageBytes < partitionCount) {
            throw new IllegalArgumentException("CPU profile storage bytes must provide at least one byte per partition");
        }
        if (partitionCount == 0 && storageBytes > 0) {
            throw new IllegalArgumentException("CPU profile with storage bytes must expose at least one partition");
        }
    }

    /**
     * Builds a deterministic profile from named structure contributions.
     *
     * @param contributions contributions keyed by structure name
     * @return aggregate profile
     */
    public static TrinityDataCoreCpuProfile fromContributions(
                                                              Map<String, TrinityDataCoreCpuContribution> contributions) {
        Map<String, TrinityDataCoreCpuContribution> sorted = new TreeMap<>(contributions);

        long storageBytes = 0L;
        int coProcessors = 0;
        int partitionCount = 0;
        CpuSelectionMode selectionMode = CpuSelectionMode.ANY;
        for (Map.Entry<String, TrinityDataCoreCpuContribution> entry : sorted.entrySet()) {
            String structureName = entry.getKey();
            if (structureName.isBlank()) {
                throw new IllegalArgumentException("CPU contribution structure name must not be blank");
            }
            TrinityDataCoreCpuContribution contribution = entry.getValue();
            storageBytes = Math.addExact(storageBytes, contribution.storageBytes());
            coProcessors = Math.addExact(coProcessors, contribution.coProcessors());
            partitionCount = Math.addExact(partitionCount, contribution.partitionCount());
            selectionMode = mergeSelectionMode(selectionMode, contribution.selectionMode(), structureName);
        }

        return new TrinityDataCoreCpuProfile(storageBytes, coProcessors, partitionCount, selectionMode);
    }

    /**
     * @return true when the profile resolves to at least one CPU partition
     */
    public boolean active() {
        return this.partitionCount > 0;
    }

    /**
     * Resolves aggregate resources into deterministic per-partition profiles.
     *
     * @return partition profiles ordered by index
     */
    public List<TrinityDataCoreCpuPartitionProfile> partitions() {
        if (!active()) {
            return List.of();
        }

        List<TrinityDataCoreCpuPartitionProfile> partitions = new ArrayList<>(this.partitionCount);
        for (int index = 0; index < this.partitionCount; index++) {
            partitions.add(new TrinityDataCoreCpuPartitionProfile(
                    index,
                    this.partitionCount,
                    this.storageBytes,
                    this.coProcessors,
                    this.selectionMode));
        }
        return List.copyOf(partitions);
    }

    private static CpuSelectionMode mergeSelectionMode(CpuSelectionMode current,
                                                       CpuSelectionMode next,
                                                       String structureName) {
        if (current == CpuSelectionMode.ANY) {
            return next;
        }
        if (next == CpuSelectionMode.ANY || current == next) {
            return current;
        }
        throw new IllegalArgumentException(
                "Conflicting CPU selection mode contribution from structure '" + structureName + "'");
    }
}
