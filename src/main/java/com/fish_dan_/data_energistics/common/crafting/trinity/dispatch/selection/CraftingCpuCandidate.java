package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.selection;

import appeng.api.config.CpuSelectionMode;

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
        this.online = requireBoolean(builder.online, "online");
        this.acceptsJob = requireBoolean(builder.acceptsJob, "acceptsJob");
        this.shared = requireBoolean(builder.shared, "shared");
        this.storageBytes = requireNonNegative(builder.storageBytes, "storageBytes");
        this.coProcessors = Math.toIntExact(requireNonNegative(builder.coProcessors, "coProcessors"));
        this.activeJobs = Math.toIntExact(requireNonNegative(builder.activeJobs, "activeJobs"));
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

    private static boolean requireBoolean(Boolean value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("Crafting CPU candidate field '" + fieldName + "' must be provided");
        }
        return value;
    }

    private static long requireNonNegative(Long value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("Crafting CPU candidate field '" + fieldName + "' must be provided");
        }
        if (value < 0L) {
            throw new IllegalArgumentException("Crafting CPU candidate field '" + fieldName + "' must not be negative");
        }
        return value;
    }

    /**
     * Builder for the complete immutable CPU fact set.
     */
    public static final class Builder {

        /**
         * Stable identity awaiting explicit collection.
         */
        private String stableIdentity;
        /**
         * CPU kind awaiting explicit collection.
         */
        private CraftingCpuKind kind;
        /**
         * Source-selection mode awaiting explicit collection.
         */
        private CpuSelectionMode selectionMode;
        /**
         * Online state awaiting explicit collection.
         */
        private Boolean online;
        /**
         * Job-acceptance state awaiting explicit collection.
         */
        private Boolean acceptsJob;
        /**
         * Shared-hardware state awaiting explicit collection.
         */
        private Boolean shared;
        /**
         * Storage fact awaiting explicit collection.
         */
        private Long storageBytes;
        /**
         * Co-processor fact awaiting explicit collection.
         */
        private Long coProcessors;
        /**
         * Occupied-job fact awaiting explicit collection.
         */
        private Long activeJobs;
        /**
         * Recent operation fact awaiting explicit collection.
         */
        private Long recentOperationLoad;

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
            return this;
        }

        /**
         * Sets whether the candidate can accept a new job.
         */
        public Builder acceptsJob(boolean acceptsJob) {
            this.acceptsJob = acceptsJob;
            return this;
        }

        /**
         * Sets whether the candidate represents shared hardware.
         */
        public Builder shared(boolean shared) {
            this.shared = shared;
            return this;
        }

        /**
         * Sets the complete storage available to one job.
         */
        public Builder storageBytes(long storageBytes) {
            this.storageBytes = storageBytes;
            return this;
        }

        /**
         * Sets the complete co-processor count.
         */
        public Builder coProcessors(int coProcessors) {
            this.coProcessors = (long) coProcessors;
            return this;
        }

        /**
         * Sets the current occupied job count.
         */
        public Builder activeJobs(int activeJobs) {
            this.activeJobs = (long) activeJobs;
            return this;
        }

        /**
         * Sets the recent physical-operation load.
         */
        public Builder recentOperationLoad(long recentOperationLoad) {
            this.recentOperationLoad = recentOperationLoad;
            return this;
        }

        /**
         * Builds the validated immutable candidate facts.
         */
        public CraftingCpuCandidate build() {
            return new CraftingCpuCandidate(this);
        }
    }
}
