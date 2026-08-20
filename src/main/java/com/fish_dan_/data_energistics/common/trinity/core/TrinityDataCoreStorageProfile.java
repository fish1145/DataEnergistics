package com.fish_dan_.data_energistics.common.trinity.core;

import java.math.BigInteger;

/**
 * Storage capability resolved from trinity storage core blocks in a formed main structure.
 */
public record TrinityDataCoreStorageProfile(BigInteger totalCapacity,
                                            int typeCapacity,
                                            int coreCount,
                                            int fullCoreCount,
                                            boolean unlimited) {

    public static final BigInteger AMOUNT_PER_M = BigInteger.valueOf(1_048_576L);
    /** Largest combined capacity that remains representable by the long-based finite storage boundary. */
    private static final BigInteger MAX_FINITE_CAPACITY = BigInteger.valueOf(Long.MAX_VALUE);
    public static final TrinityDataCoreStorageProfile EMPTY = new TrinityDataCoreStorageProfile(BigInteger.ZERO, 0, 0, 0, false);
    public static final TrinityDataCoreStorageProfile UNLIMITED = new TrinityDataCoreStorageProfile(BigInteger.ZERO, Integer.MAX_VALUE, 0, 0, true);

    public TrinityDataCoreStorageProfile {
        if (totalCapacity.signum() < 0) {
            throw new IllegalArgumentException("Storage total capacity must not be negative");
        }
        if (typeCapacity < 0) {
            throw new IllegalArgumentException("Storage type capacity must not be negative");
        }
        if (coreCount < 0) {
            throw new IllegalArgumentException("Storage core count must not be negative");
        }
        if (fullCoreCount < 0) {
            throw new IllegalArgumentException("Full storage core count must not be negative");
        }
        if (!unlimited && coreCount > 0 && totalCapacity.signum() == 0) {
            throw new IllegalArgumentException("Finite storage profile with cores must expose capacity");
        }
        if (!unlimited && coreCount > 0 && typeCapacity == 0) {
            throw new IllegalArgumentException("Finite storage profile with cores must expose type capacity");
        }
    }

    /**
     * Creates a builder for a structure with a known number of storage core positions.
     */
    public static Builder builder(int fullCoreCount) {
        return new Builder(fullCoreCount);
    }

    /**
     * Reads a storage core's exact total stored amount capacity.
     */
    public static BigInteger amountCapacity(TrinityCoreComponent component) {
        if (!component.contributesToKind(TrinityCoreKind.STORAGE_TYPES)) {
            throw new IllegalArgumentException("Only storage type cores contribute storage amount capacity");
        }
        return BigInteger.valueOf(component.byteCapacity());
    }

    /**
     * Returns whether the profile can store anything.
     */
    public boolean available() {
        return this.unlimited || (this.typeCapacity > 0 && this.totalCapacity.signum() > 0);
    }

    /**
     * Builder that accumulates storage core metadata found while scanning the main structure.
     */
    public static final class Builder {

        private final int fullCoreCount;
        private BigInteger totalCapacity = BigInteger.ZERO;
        private int typeCapacity;
        private int coreCount;

        private Builder(int fullCoreCount) {
            if (fullCoreCount < 0) {
                throw new IllegalArgumentException("Full storage core count must not be negative");
            }
            this.fullCoreCount = fullCoreCount;
        }

        /**
         * Adds one storage core contribution to this profile.
         */
        public void add(TrinityCoreComponent component) {
            if (!component.contributesToKind(TrinityCoreKind.STORAGE_TYPES)) {
                return;
            }
            this.totalCapacity = this.totalCapacity.add(amountCapacity(component));
            this.typeCapacity = Math.addExact(this.typeCapacity, component.capacityValue());
            this.coreCount = Math.addExact(this.coreCount, 1);
        }

        /**
         * Builds the immutable storage profile.
         */
        public TrinityDataCoreStorageProfile build() {
            boolean unlimited = this.totalCapacity.compareTo(MAX_FINITE_CAPACITY) > 0;
            return new TrinityDataCoreStorageProfile(
                    this.totalCapacity,
                    this.typeCapacity,
                    this.coreCount,
                    this.fullCoreCount,
                    unlimited);
        }
    }
}
