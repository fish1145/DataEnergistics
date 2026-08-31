package com.fish_dan_.data_energistics.common.crafting.trinity.capacity;

import java.math.BigInteger;

/** Exact Trinity CPU storage admission domain. Unlimited never participates in finite arithmetic. */
public sealed interface TrinityCpuStorageCapacity
                                                  permits TrinityCpuStorageCapacity.Finite, TrinityCpuStorageCapacity.Unlimited {

    /** Decodes the legacy long representation used before capacities became typed. */
    static TrinityCpuStorageCapacity fromLegacyLong(long storageBytes) {
        if (storageBytes < 0L) {
            throw new IllegalArgumentException("A legacy Trinity CPU storage capacity cannot be negative");
        }
        return storageBytes == Long.MAX_VALUE ? Unlimited.INSTANCE :
                new Finite(BigInteger.valueOf(storageBytes));
    }

    /** Creates an exact finite capacity from a non-negative component contribution. */
    static TrinityCpuStorageCapacity finite(long storageBytes) {
        return new Finite(BigInteger.valueOf(storageBytes));
    }

    /** Returns whether this CPU can own the exact compact-plan byte charge. */
    boolean accepts(BigInteger requiredBytes);

    /** Adds capacities without encoding unlimited as a numeric sentinel. */
    default TrinityCpuStorageCapacity plus(TrinityCpuStorageCapacity other) {
        if (this instanceof Unlimited || other instanceof Unlimited) {
            return Unlimited.INSTANCE;
        }
        return new Finite(((Finite) this).bytes.add(((Finite) other).bytes));
    }

    /** Returns the larger capacity without encoding unlimited as a number. */
    default TrinityCpuStorageCapacity max(TrinityCpuStorageCapacity other) {
        if (this instanceof Unlimited || other instanceof Unlimited) {
            return Unlimited.INSTANCE;
        }
        Finite left = (Finite) this;
        Finite right = (Finite) other;
        return left.bytes.compareTo(right.bytes) >= 0 ? left : right;
    }

    /** Human-readable exact diagnostic value. */
    String diagnosticValue();

    /** Projects this typed value only at AE2's public long storage boundary. */
    default long toAe2Long() {
        if (this instanceof Unlimited) {
            return Long.MAX_VALUE;
        }
        BigInteger bytes = ((Finite) this).bytes;
        return bytes.bitLength() > 63 ? Long.MAX_VALUE : bytes.longValueExact();
    }

    /** Returns whether this capacity has no finite storage. */
    default boolean isZero() {
        return this instanceof Finite finite && finite.bytes.signum() == 0;
    }

    /** Finite exact storage. */
    record Finite(BigInteger bytes) implements TrinityCpuStorageCapacity {

        public Finite {
            if (bytes.signum() < 0) {
                throw new IllegalArgumentException("A finite Trinity CPU storage capacity cannot be negative");
            }
        }

        @Override
        public boolean accepts(BigInteger requiredBytes) {
            return this.bytes.compareTo(requiredBytes) >= 0;
        }

        @Override
        public String diagnosticValue() {
            return this.bytes.toString();
        }
    }

    /** Fully populated maximum-height CPU storage. */
    enum Unlimited implements TrinityCpuStorageCapacity {

        INSTANCE;

        @Override
        public boolean accepts(BigInteger requiredBytes) {
            return true;
        }

        @Override
        public String diagnosticValue() {
            return "unlimited";
        }
    }
}
