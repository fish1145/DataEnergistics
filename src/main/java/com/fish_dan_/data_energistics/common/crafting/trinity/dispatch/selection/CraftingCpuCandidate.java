package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.selection;

import appeng.api.config.CpuSelectionMode;

import org.jspecify.annotations.Nullable;

/**
 * Immutable crafting CPU facts collected on the server thread without retaining a mutable CPU or grid reference.
 */
public final class CraftingCpuCandidate {

    /**
     * Stable identity used to recover the server-thread submission handle and determine final order.
     */
    private final String stableIdentity;
    /**
     * Explicitly supported CPU ownership boundary.
     */
    private final CraftingCpuKind kind;
    /**
     * AE2 source-selection rule exposed by this candidate.
     */
    private final CpuSelectionMode selectionMode;
    /**
     * Whether the candidate is attached to its current grid and online.
     */
    private final boolean online;
    /**
     * Whether the candidate can accept a new job without sharing an occupied worker.
     */
    private final boolean acceptsJob;
    /**
     * Whether this candidate represents shared hardware that cannot be auto-selected.
     */
    private final boolean shared;
    /**
     * Complete storage available to one job on this candidate.
     */
    private final long storageBytes;
    /**
     * Complete co-processor count used by the AE2 power preference.
     */
    private final int coProcessors;
    /**
     * Current number of occupied job slots used for load balancing.
     */
    private final int activeJobs;
    /**
     * Physical operations observed in the recent rolling load window.
     */
    private final long recentOperationLoad;

    private CraftingCpuCandidate(Builder builder) {
        if (builder.stableIdentity == null) {
            throw new IllegalStateException("Crafting CPU stable identity must be provided");
        }
        if (builder.stableIdentity.isBlank()) {
            throw new IllegalArgumentException("Crafting CPU stable identity must not be blank");
        }
        if (builder.kind == null) {
            throw new IllegalStateException("Crafting CPU kind must be provided");
        }
        if (builder.selectionMode == null) {
            throw new IllegalStateException("Crafting CPU selection mode must be provided");
        }
        this.stableIdentity = builder.stableIdentity;
        this.kind = builder.kind;
        this.selectionMode = builder.selectionMode;
        builder.requirePrimitiveFields();
        this.online = builder.online;
        this.acceptsJob = builder.acceptsJob;
        this.shared = builder.shared;
        this.storageBytes = requireNonNegative(builder.storageBytes, "storageBytes");
        this.coProcessors = requireNonNegative(builder.coProcessors, "coProcessors");
        this.activeJobs = requireNonNegative(builder.activeJobs, "activeJobs");
        this.recentOperationLoad = requireNonNegative(builder.recentOperationLoad, "recentOperationLoad");
    }

    /**
     * Starts an explicit builder so every live fact is captured at the server-thread collection boundary.
     *
     * @return candidate builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return stable deterministic candidate identity
     */
    public String stableIdentity() {
        return this.stableIdentity;
    }

    /**
     * @return explicitly supported CPU kind
     */
    public CraftingCpuKind kind() {
        return this.kind;
    }

    /**
     * @return AE2 source-selection mode
     */
    public CpuSelectionMode selectionMode() {
        return this.selectionMode;
    }

    /**
     * @return whether the candidate is online
     */
    public boolean online() {
        return this.online;
    }

    /**
     * @return whether the candidate can accept a new job
     */
    public boolean acceptsJob() {
        return this.acceptsJob;
    }

    /**
     * @return whether the candidate represents shared hardware
     */
    public boolean shared() {
        return this.shared;
    }

    /**
     * @return complete job storage in bytes
     */
    public long storageBytes() {
        return this.storageBytes;
    }

    /**
     * @return complete co-processor count
     */
    public int coProcessors() {
        return this.coProcessors;
    }

    /**
     * @return current occupied job count
     */
    public int activeJobs() {
        return this.activeJobs;
    }

    /**
     * @return recent physical-operation load
     */
    public long recentOperationLoad() {
        return this.recentOperationLoad;
    }

    private static long requireNonNegative(long value, String fieldName) {
        if (value < 0L) {
            throw new IllegalArgumentException("Crafting CPU candidate field '" + fieldName + "' must not be negative");
        }
        return value;
    }

    private static int requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException("Crafting CPU candidate field '" + fieldName + "' must not be negative");
        }
        return value;
    }

    /**
     * Builder for the complete immutable CPU fact set.
     */
    public static final class Builder {

        private static final int ONLINE_SET = 1;
        private static final int ACCEPTS_JOB_SET = 1 << 1;
        private static final int SHARED_SET = 1 << 2;
        private static final int STORAGE_BYTES_SET = 1 << 3;
        private static final int CO_PROCESSORS_SET = 1 << 4;
        private static final int ACTIVE_JOBS_SET = 1 << 5;
        private static final int RECENT_OPERATION_LOAD_SET = 1 << 6;
        private static final int REQUIRED_PRIMITIVE_FIELDS = ONLINE_SET | ACCEPTS_JOB_SET | SHARED_SET | STORAGE_BYTES_SET | CO_PROCESSORS_SET | ACTIVE_JOBS_SET | RECENT_OPERATION_LOAD_SET;

        /**
         * Stable identity awaiting explicit collection.
         */
        private @Nullable String stableIdentity;
        /**
         * CPU kind awaiting explicit collection.
         */
        private @Nullable CraftingCpuKind kind;
        /**
         * Source-selection mode awaiting explicit collection.
         */
        private @Nullable CpuSelectionMode selectionMode;
        /**
         * Online state awaiting explicit collection.
         */
        private boolean online;
        /**
         * Job-acceptance state awaiting explicit collection.
         */
        private boolean acceptsJob;
        /**
         * Shared-hardware state awaiting explicit collection.
         */
        private boolean shared;
        /**
         * Storage fact awaiting explicit collection.
         */
        private long storageBytes;
        /**
         * Co-processor fact awaiting explicit collection.
         */
        private int coProcessors;
        /**
         * Occupied-job fact awaiting explicit collection.
         */
        private int activeJobs;
        /**
         * Recent operation fact awaiting explicit collection.
         */
        private long recentOperationLoad;
        /**
         * Tracks which required primitive facts were supplied without using nullable wrapper values.
         */
        private int primitiveFieldsSet;

        private Builder() {}

        /**
         * Sets the stable deterministic identity.
         */
        public Builder stableIdentity(String stableIdentity) {
            this.stableIdentity = stableIdentity;
            return this;
        }

        /**
         * Sets the explicitly supported CPU kind.
         */
        public Builder kind(CraftingCpuKind kind) {
            this.kind = kind;
            return this;
        }

        /**
         * Sets the AE2 source-selection mode.
         */
        public Builder selectionMode(CpuSelectionMode selectionMode) {
            this.selectionMode = selectionMode;
            return this;
        }

        /**
         * Sets whether the candidate is online.
         */
        public Builder online(boolean online) {
            this.online = online;
            this.primitiveFieldsSet |= ONLINE_SET;
            return this;
        }

        /**
         * Sets whether the candidate can accept a new job.
         */
        public Builder acceptsJob(boolean acceptsJob) {
            this.acceptsJob = acceptsJob;
            this.primitiveFieldsSet |= ACCEPTS_JOB_SET;
            return this;
        }

        /**
         * Sets whether the candidate represents shared hardware.
         */
        public Builder shared(boolean shared) {
            this.shared = shared;
            this.primitiveFieldsSet |= SHARED_SET;
            return this;
        }

        /**
         * Sets the complete storage available to one job.
         */
        public Builder storageBytes(long storageBytes) {
            this.storageBytes = storageBytes;
            this.primitiveFieldsSet |= STORAGE_BYTES_SET;
            return this;
        }

        /**
         * Sets the complete co-processor count.
         */
        public Builder coProcessors(int coProcessors) {
            this.coProcessors = coProcessors;
            this.primitiveFieldsSet |= CO_PROCESSORS_SET;
            return this;
        }

        /**
         * Sets the current occupied job count.
         */
        public Builder activeJobs(int activeJobs) {
            this.activeJobs = activeJobs;
            this.primitiveFieldsSet |= ACTIVE_JOBS_SET;
            return this;
        }

        /**
         * Sets the recent physical-operation load.
         */
        public Builder recentOperationLoad(long recentOperationLoad) {
            this.recentOperationLoad = recentOperationLoad;
            this.primitiveFieldsSet |= RECENT_OPERATION_LOAD_SET;
            return this;
        }

        /**
         * Builds the validated immutable candidate facts.
         */
        public CraftingCpuCandidate build() {
            return new CraftingCpuCandidate(this);
        }

        private void requirePrimitiveFields() {
            if (this.primitiveFieldsSet != REQUIRED_PRIMITIVE_FIELDS) {
                throw new IllegalStateException("All crafting CPU primitive facts must be provided");
            }
        }
    }
}
