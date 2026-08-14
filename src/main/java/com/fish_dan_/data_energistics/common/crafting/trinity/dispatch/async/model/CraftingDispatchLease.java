package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model;

import java.util.UUID;

/**
 * Immutable generation token proving which worker work item an asynchronous dispatch proposal belongs to.
 *
 * <p>
 * Only process-local identities and scalar generations cross the thread boundary. Grid objects, jobs and execution
 * state remain owned by the server thread and must be revalidated before a proposal may reach the synchronous commit
 * path.
 * </p>
 *
 * @param gridGeneration       process-local generation of the service-grid provider publication
 * @param runtimeId            stable Trinity Data Core host identity
 * @param runtimeGeneration    process-local runtime instance generation
 * @param workerNumber         concrete worker partition number
 * @param jobId                persistent AE2 crafting-link identity
 * @param jobRevision          transient job mutation revision
 * @param workGeneration       compact-plan generation, or legacy task lease generation
 * @param routeLeaseEpoch      information exchange depot lease epoch
 * @param membershipGeneration VirtualGrid membership generation
 */
public record CraftingDispatchLease(
                                    long gridGeneration,
                                    UUID runtimeId,
                                    long runtimeGeneration,
                                    int workerNumber,
                                    UUID jobId,
                                    long jobRevision,
                                    long workGeneration,
                                    long routeLeaseEpoch,
                                    long membershipGeneration) {

    public CraftingDispatchLease {
        if (gridGeneration <= 0L) {
            throw new IllegalArgumentException("Crafting dispatch grid generation must be positive");
        }
        if (runtimeId == null) {
            throw new IllegalArgumentException("Crafting dispatch runtime identity must not be null");
        }
        if (runtimeGeneration <= 0L) {
            throw new IllegalArgumentException("Crafting dispatch runtime generation must be positive");
        }
        if (workerNumber <= 0) {
            throw new IllegalArgumentException("Crafting dispatch worker number must be positive");
        }
        if (jobId == null) {
            throw new IllegalArgumentException("Crafting dispatch job identity must not be null");
        }
        if (jobRevision < 0L) {
            throw new IllegalArgumentException("Crafting dispatch job revision must not be negative");
        }
        if (workGeneration < 0L) {
            throw new IllegalArgumentException("Crafting dispatch work generation must not be negative");
        }
        if (routeLeaseEpoch < 0L) {
            throw new IllegalArgumentException("Crafting dispatch route lease epoch must not be negative");
        }
        if (membershipGeneration < 0L) {
            throw new IllegalArgumentException("Crafting dispatch membership generation must not be negative");
        }
    }
}
