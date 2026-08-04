package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.budget;

/**
 * Immutable hard limits for one grid-shared crafting dispatch window.
 *
 * @param maxAttemptsPerGrid       maximum physical provider calls across the grid
 * @param maxAttemptsPerProvider   maximum physical calls assigned to one provider
 * @param maxServerSubmissionNanos maximum measured server-thread submission time
 * @param maxCapacityCaptureNanos  maximum measured server-thread provider-capacity capture time
 */
public record CraftingDispatchLimits(
                                     int maxAttemptsPerGrid,
                                     int maxAttemptsPerProvider,
                                     long maxServerSubmissionNanos,
                                     long maxCapacityCaptureNanos) {

    /** Baseline provider ceiling retained from the pre-reorganization dispatch window. */
    public static final int DEFAULT_MAX_ATTEMPTS_PER_PROVIDER = 16;

    /** Baseline grid ceiling supports sixteen providers at their complete provider quota. */
    public static final int DEFAULT_MAX_ATTEMPTS_PER_GRID = DEFAULT_MAX_ATTEMPTS_PER_PROVIDER * 16;

    /**
     * Preliminary server submission budget calibrated above the observed Phase 0 256-worker maximum.
     */
    public static final long DEFAULT_MAX_SERVER_SUBMISSION_NANOS = 30_000_000L;

    /** Capacity simulation has an independent per-grid ceiling so it cannot consume the commit budget invisibly. */
    public static final long DEFAULT_MAX_CAPACITY_CAPTURE_NANOS = 4_000_000L;

    /** Fixed Phase 2 safety limits used until the adaptive governor is introduced. */
    public static final CraftingDispatchLimits DEFAULT = new CraftingDispatchLimits(
            DEFAULT_MAX_ATTEMPTS_PER_GRID,
            DEFAULT_MAX_ATTEMPTS_PER_PROVIDER,
            DEFAULT_MAX_SERVER_SUBMISSION_NANOS,
            DEFAULT_MAX_CAPACITY_CAPTURE_NANOS);

    /**
     * Retains the Phase 2 construction boundary while applying the production capacity-capture ceiling.
     *
     * @param maxAttemptsPerGrid       maximum physical provider calls across the grid
     * @param maxAttemptsPerProvider   maximum physical calls assigned to one provider
     * @param maxServerSubmissionNanos maximum measured server-thread submission time
     */
    public CraftingDispatchLimits(
                                  int maxAttemptsPerGrid,
                                  int maxAttemptsPerProvider,
                                  long maxServerSubmissionNanos) {
        this(
                maxAttemptsPerGrid,
                maxAttemptsPerProvider,
                maxServerSubmissionNanos,
                DEFAULT_MAX_CAPACITY_CAPTURE_NANOS);
    }

    /** Validates every hard limit before a dispatch window can observe it. */
    public CraftingDispatchLimits {
        if (maxAttemptsPerGrid <= 0) {
            throw new IllegalArgumentException("Grid crafting dispatch limit must be positive");
        }
        if (maxAttemptsPerProvider <= 0) {
            throw new IllegalArgumentException("Provider crafting dispatch limit must be positive");
        }
        if (maxServerSubmissionNanos <= 0L) {
            throw new IllegalArgumentException("Server crafting submission time limit must be positive");
        }
        if (maxCapacityCaptureNanos <= 0L) {
            throw new IllegalArgumentException("Provider capacity capture time limit must be positive");
        }
    }
}
