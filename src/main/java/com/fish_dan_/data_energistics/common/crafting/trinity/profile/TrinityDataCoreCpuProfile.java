package com.fish_dan_.data_energistics.common.crafting.trinity.profile;

import com.fish_dan_.data_energistics.common.crafting.trinity.capacity.TrinityCpuStorageCapacity;

import appeng.api.config.CpuSelectionMode;

import java.math.BigInteger;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregate CPU data for a formed Trinity Data Core host.
 *
 * <p>
 * The profile is built from named structure contributions and resolves them into stable virtual workers. Every worker
 * receives the complete storage value; worker resources are not divided by the worker count. Active Trinity CPUs expose
 * unlimited AE2 co-processor capacity because physical dispatch is bounded by the configured Trinity dispatch limits.
 */
public record TrinityDataCoreCpuProfile(TrinityCpuStorageCapacity storageCapacity,
                                        int coProcessors,
                                        int partitionCount,
                                        CpuSelectionMode selectionMode) {

    public static final int MAX_PARTITION_COUNT = TrinityDataCoreCpuContribution.MAX_PARTITION_COUNT;
    public static final int DEFAULT_CO_PROCESSORS = Integer.MAX_VALUE;
    public static final TrinityDataCoreCpuProfile EMPTY = new TrinityDataCoreCpuProfile(
            new TrinityCpuStorageCapacity.Finite(BigInteger.ZERO),
            0,
            0,
            CpuSelectionMode.ANY);

    public TrinityDataCoreCpuProfile {
        if (coProcessors < 0) {
            throw new IllegalArgumentException("CPU profile co-processors must not be negative");
        }
        if (partitionCount < 0) {
            throw new IllegalArgumentException("CPU profile partition count must not be negative");
        }
        if (partitionCount > MAX_PARTITION_COUNT) {
            throw new IllegalArgumentException("CPU profile partition count must not exceed " + MAX_PARTITION_COUNT);
        }
        if (partitionCount > 0 && storageCapacity.isZero()) {
            throw new IllegalArgumentException("CPU profile with workers must provide positive storage bytes");
        }
        if (partitionCount == 0 && !storageCapacity.isZero()) {
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

        TrinityCpuStorageCapacity storageCapacity = new TrinityCpuStorageCapacity.Finite(BigInteger.ZERO);
        int partitionCount = 0;
        CpuSelectionMode selectionMode = CpuSelectionMode.ANY;
        for (Map.Entry<String, TrinityDataCoreCpuContribution> entry : sorted.entrySet()) {
            String structureName = entry.getKey();
            if (structureName.isBlank()) {
                throw new IllegalArgumentException("CPU contribution structure name must not be blank");
            }
            TrinityDataCoreCpuContribution contribution = entry.getValue();
            storageCapacity = storageCapacity.plus(contribution.storageCapacity());
            partitionCount = Math.addExact(partitionCount, contribution.partitionCount());
            selectionMode = mergeSelectionMode(selectionMode, contribution.selectionMode(), structureName);
        }

        int coProcessors = partitionCount > 0 ? DEFAULT_CO_PROCESSORS : 0;
        return new TrinityDataCoreCpuProfile(storageCapacity, coProcessors, partitionCount, selectionMode);
    }

    /**
     * @return true when the profile resolves to at least one CPU partition
     */
    public boolean active() {
        return this.partitionCount > 0;
    }

    /** Resolves resources for the reserved CPU or one worker without materializing the entire worker pool. */
    public TrinityDataCoreCpuPartitionProfile partition(int number) {
        if (!active()) {
            throw new IllegalStateException("Inactive CPU profile cannot resolve a virtual CPU");
        }
        return new TrinityDataCoreCpuPartitionProfile(
                number,
                this.partitionCount,
                this.storageCapacity,
                this.coProcessors,
                this.selectionMode);
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
