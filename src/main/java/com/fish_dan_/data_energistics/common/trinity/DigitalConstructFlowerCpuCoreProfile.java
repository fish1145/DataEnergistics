package com.fish_dan_.data_energistics.common.trinity;

import com.fish_dan_.data_energistics.common.crafting.flower.DigitalConstructFlowerCpuContribution;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Objects;

/**
 * CPU capability resolved from trinity merged storage core blocks in formed child structures.
 */
public record DigitalConstructFlowerCpuCoreProfile(long storageBytes,
                                                   int coProcessors,
                                                   int filledCoreSlots,
                                                   int fullCoreSlots,
                                                   int actualRepeatCount,
                                                   int maxRepeatCount,
                                                   int maxThreads) {

    private static final BigInteger PARALLEL_VALUE_PER_M = BigInteger.valueOf(2L);

    public static final int CORE_SLOT_START_Y = 0;
    public static final int CORE_SLOT_END_Y = 15;
    public static final int REPEAT_START_Y = 3;
    public static final int REPEAT_END_Y = 15;
    public static final int FULL_CORE_SLOT_COUNT = 256;
    public static final int MAX_REPEAT_COUNT = 13;
    public static final int MAX_THREADS = 256;
    public static final int CONTROLLER_LOCAL_Y = 0;
    public static final DigitalConstructFlowerCpuCoreProfile EMPTY = new DigitalConstructFlowerCpuCoreProfile(
            0L,
            0,
            0,
            FULL_CORE_SLOT_COUNT,
            0,
            MAX_REPEAT_COUNT,
            MAX_THREADS);

    public DigitalConstructFlowerCpuCoreProfile {
        if (storageBytes < 0) {
            throw new IllegalArgumentException("CPU core profile storage bytes must not be negative");
        }
        if (coProcessors < 0) {
            throw new IllegalArgumentException("CPU core profile co-processors must not be negative");
        }
        if (filledCoreSlots < 0) {
            throw new IllegalArgumentException("CPU core profile filled core slots must not be negative");
        }
        if (fullCoreSlots <= 0) {
            throw new IllegalArgumentException("CPU core profile full core slots must be positive");
        }
        if (filledCoreSlots > fullCoreSlots) {
            throw new IllegalArgumentException("CPU core profile filled core slots must not exceed full core slots");
        }
        if (actualRepeatCount < 0) {
            throw new IllegalArgumentException("CPU core profile repeat count must not be negative");
        }
        if (maxRepeatCount <= 0) {
            throw new IllegalArgumentException("CPU core profile max repeat count must be positive");
        }
        if (maxThreads <= 0) {
            throw new IllegalArgumentException("CPU core profile max threads must be positive");
        }
        if (filledCoreSlots == 0 && (storageBytes > 0 || coProcessors > 0)) {
            throw new IllegalArgumentException("CPU core profile with capacity must expose at least one filled core");
        }
    }

    /**
     * Creates a builder for merged storage cores found in one child CPU structure.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Counts the continuous matched repeated CPU layers starting at the first repeat layer.
     */
    public static int actualRepeatCount(Collection<Integer> localLayers) {
        Objects.requireNonNull(localLayers, "localLayers");
        int repeatCount = 0;
        for (int localY = REPEAT_START_Y; localY <= REPEAT_END_Y; localY++) {
            if (!localLayers.contains(localY)) {
                break;
            }
            repeatCount++;
        }
        return repeatCount;
    }

    /**
     * Converts a merged storage core's M/G tier value into AE2 crafting storage bytes.
     */
    public static long craftingStorageBytes(TrinityCoreComponent component) {
        Objects.requireNonNull(component, "component");
        if (component.kind() != TrinityCoreKind.PARALLEL_CPU) {
            throw new IllegalArgumentException("Only merged storage CPU cores contribute crafting storage bytes");
        }
        BigInteger bytes = BigInteger.valueOf(component.capacityValue())
                .multiply(DigitalConstructFlowerStorageProfile.AMOUNT_PER_M)
                .divide(PARALLEL_VALUE_PER_M);
        return bytes.longValueExact();
    }

    /**
     * Converts this profile into the runtime contribution object used by the host.
     */
    public DigitalConstructFlowerCpuContribution contribution() {
        if (this.filledCoreSlots == 0 || this.actualRepeatCount == 0) {
            return DigitalConstructFlowerCpuContribution.EMPTY;
        }
        if (fullCpu()) {
            return DigitalConstructFlowerCpuContribution.of(Long.MAX_VALUE, Integer.MAX_VALUE, threadCount());
        }
        return DigitalConstructFlowerCpuContribution.of(this.storageBytes, this.coProcessors, threadCount());
    }

    /**
     * Returns true when every core slot is filled and the repeated CPU section reaches its maximum height.
     */
    public boolean fullCpu() {
        return this.filledCoreSlots == this.fullCoreSlots && this.actualRepeatCount == this.maxRepeatCount;
    }

    /**
     * Maps the matched repeated section height to the exposed virtual CPU count.
     */
    public int threadCount() {
        if (this.actualRepeatCount == 0) {
            return 0;
        }
        int cappedRepeatCount = Math.min(this.actualRepeatCount, this.maxRepeatCount);
        int threads = Math.floorDiv(Math.multiplyExact(cappedRepeatCount, this.maxThreads), this.maxRepeatCount);
        return Math.max(1, Math.min(threads, this.maxThreads));
    }

    /**
     * Builder that accumulates merged storage core metadata found while scanning a CPU child structure.
     */
    public static final class Builder {

        private long storageBytes;
        private int coProcessors;
        private int filledCoreSlots;
        private int actualRepeatCount;

        private Builder() {}

        /**
         * Records the matched repeated CPU section height.
         */
        public Builder actualRepeatCount(int actualRepeatCount) {
            if (actualRepeatCount < 0) {
                throw new IllegalArgumentException("CPU repeat count must not be negative");
            }
            this.actualRepeatCount = actualRepeatCount;
            return this;
        }

        /**
         * Adds one merged storage core contribution to this profile.
         */
        public void add(TrinityCoreComponent component) {
            Objects.requireNonNull(component, "component");
            if (component.kind() != TrinityCoreKind.PARALLEL_CPU) {
                return;
            }
            this.storageBytes = Math.addExact(this.storageBytes, craftingStorageBytes(component));
            this.coProcessors = Math.addExact(this.coProcessors, component.capacityValue());
            this.filledCoreSlots = Math.addExact(this.filledCoreSlots, 1);
        }

        /**
         * Builds the immutable CPU core profile.
         */
        public DigitalConstructFlowerCpuCoreProfile build() {
            if (this.filledCoreSlots == 0 || this.actualRepeatCount == 0) {
                return EMPTY;
            }
            return new DigitalConstructFlowerCpuCoreProfile(
                    this.storageBytes,
                    this.coProcessors,
                    this.filledCoreSlots,
                    FULL_CORE_SLOT_COUNT,
                    this.actualRepeatCount,
                    MAX_REPEAT_COUNT,
                    MAX_THREADS);
        }
    }
}
