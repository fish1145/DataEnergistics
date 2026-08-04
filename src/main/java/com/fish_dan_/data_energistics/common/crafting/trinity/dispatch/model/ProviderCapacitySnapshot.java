package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model;

import java.util.Optional;

/**
 * Immutable server-thread capacity observation safe to pass to pure planning code.
 *
 * <p>
 * The snapshot deliberately contains no grid, level, block entity, provider, or other mutable world reference.
 * Correctness depends on revalidating its publication revision before commit; the capture tick is diagnostic only.
 * </p>
 *
 * @param providerId          provider publication observed during capture
 * @param route               provider-local side, lane, connection, or conservative provider route
 * @param machineTargetId     provider-independent machine identity when the adapter can prove one
 * @param patternIdentity     immutable pattern signature understood by the caller
 * @param publicationRevision provider-index revision observed during capture
 * @param capacityRevision    counted-capability registry revision observed during capture
 * @param captureTick         server tick at which the facts were captured
 * @param routingMode         proven routing contract represented by this snapshot
 * @param capacity            currently available logical craft capacity
 * @param maximumSingleBatch  non-negative known upper bound for one physical submission, or explicitly unknown
 */
public record ProviderCapacitySnapshot(
                                       CraftingProviderId providerId,
                                       CraftingDispatchTarget route,
                                       Optional<MachineTargetId> machineTargetId,
                                       String patternIdentity,
                                       long publicationRevision,
                                       long capacityRevision,
                                       long captureTick,
                                       ProviderRoutingMode routingMode,
                                       DispatchCapacity capacity,
                                       DispatchCapacity maximumSingleBatch) {

    public ProviderCapacitySnapshot {
        if (providerId == null) {
            throw new IllegalArgumentException("Provider capacity publication identity must not be null");
        }
        if (route == null) {
            throw new IllegalArgumentException("Provider capacity route must not be null");
        }
        if (machineTargetId == null) {
            throw new IllegalArgumentException("Provider capacity machine target identity must not be null");
        }
        if (patternIdentity == null || patternIdentity.isBlank()) {
            throw new IllegalArgumentException("Provider capacity pattern identity must not be blank");
        }
        if (publicationRevision < 0L) {
            throw new IllegalArgumentException("Provider capacity publication revision must not be negative");
        }
        if (capacityRevision < 0L) {
            throw new IllegalArgumentException("Provider capacity contract revision must not be negative");
        }
        if (captureTick < 0L) {
            throw new IllegalArgumentException("Provider capacity capture tick must not be negative");
        }
        if (routingMode == null) {
            throw new IllegalArgumentException("Provider capacity routing mode must not be null");
        }
        if (capacity == null) {
            throw new IllegalArgumentException("Provider capacity must not be null");
        }
        if (maximumSingleBatch == null) {
            throw new IllegalArgumentException("Provider maximum single batch must not be null");
        }
    }
}
