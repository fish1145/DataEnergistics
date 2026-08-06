package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchCursor;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityCachedComputation;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationNamespace;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Cache implementation that separates immutable candidate calculation from mutable shard reservation. */
final class DispatchProposalCandidatePlannerImpl implements DispatchProposalCandidatePlanner {

    private final Supplier<TrinityComputationCache> cache;

    DispatchProposalCandidatePlannerImpl(Supplier<TrinityComputationCache> cache) {
        if (cache == null) {
            throw new IllegalArgumentException("Dispatch proposal candidate planning requires a cache");
        }
        this.cache = cache;
    }

    @Override
    public DispatchProposalCandidatePlan plan(
                                               CraftingDispatchProposalRequest request,
                                               BooleanSupplier lifecycleActive) {
        if (request == null || lifecycleActive == null) {
            throw new IllegalArgumentException("Dispatch proposal candidate planning requires a request and lifecycle");
        }
        CandidateKey key = new CandidateKey(
                request.capacity().key(),
                request.capacity().snapshots(),
                request.remainingCrafts(),
                request.cursor());
        try {
            return this.cache.get().computeInlineIfActive(
                            key.captureKey().gridScope(),
                            TrinityComputationNamespace.PROPOSAL_CANDIDATE,
                            key.captureKey().capacityEpoch(),
                            key,
                            lifecycleActive,
                            () -> TrinityCachedComputation.cacheable(calculate(key)))
                    .map(value -> value.value())
                    .orElseGet(() -> new DispatchProposalCandidatePlan(List.of()));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Dispatch proposal candidate cache wait was interrupted", exception);
        } catch (ExecutionException exception) {
            throw propagate(exception.getCause());
        }
    }

    private static DispatchProposalCandidatePlan calculate(CandidateKey key) {
        long maximumCount = key.remainingCrafts().longValueExact();
        ProviderTargetRotation rotation = ProviderTargetRotation.create(key.snapshots(), key.cursor());
        ArrayList<DispatchProposalCandidatePlan.Candidate> candidates = new ArrayList<>(rotation.targets().size());
        for (ProviderTargetRotation.Target target : rotation.targets()) {
            long offered = offeredCount(target.snapshot(), maximumCount);
            if (offered > 0L) {
                candidates.add(new DispatchProposalCandidatePlan.Candidate(
                        target.snapshot(),
                        offered,
                        target.successor()));
            }
        }
        return new DispatchProposalCandidatePlan(candidates);
    }

    private static long offeredCount(ProviderCapacitySnapshot target, long maximumCount) {
        return switch (target.routingMode()) {
            case TARGETED -> Math.min(effectiveTargetCapacity(target), maximumCount);
            case AGGREGATE -> maximumCount;
            case ORDERED, UNKNOWN -> 1L;
        };
    }

    private static long effectiveTargetCapacity(ProviderCapacitySnapshot target) {
        if (isKnownZero(target.capacity()) || isKnownZero(target.maximumSingleBatch())) {
            return 0L;
        }
        if (target.routingMode() != ProviderRoutingMode.TARGETED ||
                !(target.capacity() instanceof DispatchCapacity.Known(long capacity)) ||
                !(target.maximumSingleBatch() instanceof DispatchCapacity.Known(long maximumSingleBatch))) {
            return 1L;
        }
        return Math.min(capacity, maximumSingleBatch);
    }

    private static boolean isKnownZero(DispatchCapacity capacity) {
        return capacity instanceof DispatchCapacity.Known(long logicalCrafts) && logicalCrafts == 0L;
    }

    private static RuntimeException propagate(Throwable failure) {
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException("Dispatch proposal candidate cache calculation failed", failure);
    }

    /** Complete immutable proposal-candidate key retained inside the shared Grid LRU. */
    private record CandidateKey(
                                ProviderCapacityCaptureKey captureKey,
                                List<ProviderCapacitySnapshot> snapshots,
                                BigInteger remainingCrafts,
                                CraftingDispatchCursor cursor) {

        private CandidateKey {
            if (captureKey == null || remainingCrafts == null || cursor == null) {
                throw new IllegalArgumentException("Dispatch proposal candidate cache key must be complete");
            }
            snapshots = List.copyOf(snapshots);
            if (remainingCrafts.signum() <= 0 || remainingCrafts.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                throw new IllegalArgumentException("Dispatch proposal candidate work must be in the positive long domain");
            }
        }
    }
}
