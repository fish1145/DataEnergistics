package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchCursor;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchLease;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

final class DispatchComputationCacheTest {

    private final List<TrinityComputationCache> caches = new ArrayList<>();

    @AfterEach
    void closeCaches() {
        this.caches.forEach(TrinityComputationCache::close);
    }

    @Test
    void capacitySliceCacheUsesCompleteDynamicInputAndCapacityEpoch() {
        TrinityComputationCache cache = cache();
        CapacitySlicePlanner delegate = CapacitySlicePlanner.create();
        AtomicInteger calculations = new AtomicInteger();
        DispatchCapacityPlanner planner = new DispatchCapacityPlanner(
                () -> cache,
                (snapshots, remainingCrafts, physicalCallLimit, cursor) -> {
                    calculations.incrementAndGet();
                    return delegate.plan(snapshots, remainingCrafts, physicalCallLimit, cursor);
                });
        ProviderCapacityCapture capture = capture(40L, 8L, 8L);

        DispatchCapacitySlicePlan first = planner.plan(
                capture,
                BigInteger.valueOf(6L),
                1,
                CraftingDispatchCursor.initial());
        DispatchCapacitySlicePlan repeated = planner.plan(
                capture,
                BigInteger.valueOf(6L),
                1,
                CraftingDispatchCursor.initial());
        assertSame(first, repeated);
        assertEquals(1, calculations.get());

        DispatchCapacitySlicePlan changedAmount = planner.plan(
                capture,
                BigInteger.valueOf(7L),
                1,
                CraftingDispatchCursor.initial());
        DispatchCapacitySlicePlan changedCursor = planner.plan(
                capture,
                BigInteger.valueOf(6L),
                1,
                new CraftingDispatchCursor(1, 0L));
        DispatchCapacitySlicePlan changedSnapshot = planner.plan(
                capture(40L, 4L, 8L),
                BigInteger.valueOf(6L),
                1,
                CraftingDispatchCursor.initial());
        DispatchCapacitySlicePlan changedEpoch = planner.plan(
                capture(41L, 8L, 8L),
                BigInteger.valueOf(6L),
                1,
                CraftingDispatchCursor.initial());

        assertNotSame(first, changedAmount);
        assertNotSame(first, changedCursor);
        assertNotSame(first, changedSnapshot);
        assertNotSame(first, changedEpoch);
        assertEquals(5, calculations.get());
    }

    @Test
    void proposalCandidatesReuseExactResultsAndRotateProvidersBeforeTargets() {
        TrinityComputationCache cache = cache();
        DispatchProposalCandidatePlanner planner = DispatchProposalCandidatePlanner.create(() -> cache);
        ProviderCapacityCapture capture = capture(50L, 8L, 8L);
        CraftingDispatchCursor cursor = CraftingDispatchCursor.initial();
        List<String> selectedRoutes = new ArrayList<>();

        for (int attempt = 0; attempt < 5; attempt++) {
            CraftingDispatchProposalRequest request = request(capture, 6L, cursor);
            DispatchProposalCandidatePlan first = planner.plan(request);
            DispatchProposalCandidatePlan repeated = planner.plan(request);
            assertSame(first, repeated);
            DispatchProposalCandidatePlan.Candidate selected = first.candidates().getFirst();
            selectedRoutes.add(selected.target().route().stableIdentity());
            cursor = selected.nextCursor();
        }

        assertEquals(List.of("a-0", "b-0", "a-1", "b-1", "a-0"), selectedRoutes);
        DispatchProposalCandidatePlan initial = planner.plan(
                request(capture, 6L, CraftingDispatchCursor.initial()));
        assertNotSame(initial, planner.plan(request(capture, 7L, CraftingDispatchCursor.initial())));

        ProviderCapacitySnapshot firstDuplicate = capture.snapshots().getFirst();
        ProviderCapacitySnapshot secondDuplicate = new ProviderCapacitySnapshot(
                firstDuplicate.providerId(),
                firstDuplicate.route(),
                firstDuplicate.machineTargetId(),
                firstDuplicate.patternIdentity(),
                firstDuplicate.publicationRevision(),
                firstDuplicate.capacityRevision(),
                firstDuplicate.captureTick(),
                firstDuplicate.routingMode(),
                firstDuplicate.capacity(),
                firstDuplicate.maximumSingleBatch());
        ProviderCapacityCapture duplicateCapture = new ProviderCapacityCapture(
                capture.key(),
                List.of(firstDuplicate, secondDuplicate));
        DispatchProposalCandidatePlan duplicates = planner.plan(
                request(duplicateCapture, 6L, CraftingDispatchCursor.initial()));
        assertEquals(2, duplicates.candidates().size());
        assertNotSame(duplicates.candidates().getFirst().target(), duplicates.candidates().getLast().target());
        assertNotSame(initial, planner.plan(request(capture(51L, 8L, 8L), 6L, CraftingDispatchCursor.initial())));
    }

    private TrinityComputationCache cache() {
        TrinityComputationCache cache = TrinityComputationCache.create(Runnable::run, 32);
        this.caches.add(cache);
        return cache;
    }

    private static CraftingDispatchProposalRequest request(ProviderCapacityCapture capture,
                                                           long remainingCrafts,
                                                           CraftingDispatchCursor cursor) {
        return new CraftingDispatchProposalRequest(
                new CraftingDispatchLease(
                        capture.key().gridScope(),
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        1L,
                        1,
                        UUID.fromString("00000000-0000-0000-0000-000000000002"),
                        1L,
                        1L,
                        1L,
                        1L),
                capture,
                BigInteger.valueOf(remainingCrafts),
                cursor);
    }

    private static ProviderCapacityCapture capture(long epoch, long firstCapacity, long otherCapacity) {
        long scope = 1L;
        long publicationRevision = 1L;
        long capacityRevision = 1L;
        List<ProviderCapacitySnapshot> snapshots = List.of(
                snapshot(scope, 10L, "a-0", publicationRevision, capacityRevision, epoch, firstCapacity),
                snapshot(scope, 10L, "a-1", publicationRevision, capacityRevision, epoch, otherCapacity),
                snapshot(scope, 20L, "b-0", publicationRevision, capacityRevision, epoch, otherCapacity),
                snapshot(scope, 20L, "b-1", publicationRevision, capacityRevision, epoch, otherCapacity));
        ProviderCapacityCaptureKey key = new ProviderCapacityCaptureKey(
                scope,
                publicationRevision,
                capacityRevision,
                epoch,
                List.of(new CraftingProviderId(scope, 10L), new CraftingProviderId(scope, 20L)),
                "pattern",
                List.of(),
                8L);
        return new ProviderCapacityCapture(key, snapshots);
    }

    private static ProviderCapacitySnapshot snapshot(long scope,
                                                     long providerSequence,
                                                     String route,
                                                     long publicationRevision,
                                                     long capacityRevision,
                                                     long epoch,
                                                     long capacity) {
        return new ProviderCapacitySnapshot(
                new CraftingProviderId(scope, providerSequence),
                new CraftingDispatchTarget(route),
                Optional.empty(),
                "pattern",
                publicationRevision,
                capacityRevision,
                epoch,
                ProviderRoutingMode.TARGETED,
                new DispatchCapacity.Known(capacity),
                new DispatchCapacity.Known(capacity));
    }
}
